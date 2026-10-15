package com.rmh.aispringboot.AiService;

import com.rmh.aispringboot.DTO.command.ConsultationSessionCreateDTO;
import com.rmh.aispringboot.DTO.response.ConsultationMessageResponseDTO;
import com.rmh.aispringboot.entity.ConsultationSession;
import com.rmh.aispringboot.exception.BusinessException;
import com.rmh.aispringboot.service.ConsultationMessageService;
import com.rmh.aispringboot.service.ConsultationSessionService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * 心理疏导对话服务
 *
 * <p>对话记忆（ChatMemory）的读写完全交由 MessageChatMemoryAdvisor 自动管理，
 * 本类只负责三件事：前置校验、消息落库、把模型输出流原样转发出去。
 */
@Service
public class PsychologicalSupportService {

    /** 业务会话 ID 前缀，形如 session_1 */
    private static final String SESSION_ID_PREFIX = "session_";
    /** 对话记忆标识前缀，形如 conversation_session_1 */
    private static final String CONVERSATION_ID_PREFIX = "conversation_";
    /** 会话有效期：24 小时 */
    private static final long SESSION_TTL_MILLIS = 86_400_000L;
    /** 会话初始状态 */
    private static final String SESSION_STATUS_ACTIVE = "ACTIVE";
    /** 消息发送者类型：用户 */
    private static final int SENDER_TYPE_USER = 1;
    /** 模型标识，落库时记录 */
    private static final String MODEL_NAME = "openai";

    @Autowired
    @Qualifier("open-ai")
    private ChatClient chatClient;

    @Autowired
    private ConsultationSessionService consultationSessionService;

    @Autowired
    private ConsultationMessageService consultationMessageService;

    /**
     * 开启一个新的咨询会话，并保存首条用户消息
     */
    public StructOutPut.StreamChatSession startSession(Long userId, ConsultationSessionCreateDTO createDTO) {
        if (userId == null) {
            throw new BusinessException("用户未登录");
        }

        // 创建数据库会话记录
        ConsultationSession session = consultationSessionService.createSession(userId, createDTO);
        if (session == null) {
            throw new BusinessException("创建会话失败：用户不存在");
        }

        // 将初始用户消息保存到消息表
        consultationMessageService.saveUserMessage(session.getId(), createDTO.getInitialMessage(), null);

        long now = System.currentTimeMillis();
        return new StructOutPut.StreamChatSession(
                SESSION_ID_PREFIX + session.getId(),
                userId,
                createDTO.getInitialMessage(),
                now,
                now + SESSION_TTL_MILLIS,
                1,
                SESSION_STATUS_ACTIVE
        );
    }

    /**
     * 流式心理疏导对话。
     *
     * <p>直接在响应式管道上返回模型输出，不再用 Flux.create 手动桥接，
     * 这样客户端断开时，取消信号可以正常传播到模型请求。
     *
     * @param userId          当前登录用户，用于校验会话归属
     * @param sessionIdString 业务会话 ID，形如 session_1
     * @param userMessage     用户本次发送的消息
     */
    public Flux<String> streamPsychologicalChat(Long userId, String sessionIdString, String userMessage) {

        // ── 1. 前置校验与落库：同步执行，位于响应式管道之外
        if (userId == null) {
            return Flux.error(new BusinessException("用户未登录"));
        }
        Long dbSessionId = extractSessionId(sessionIdString);
        if (dbSessionId == null) {
            return Flux.error(new IllegalArgumentException("会话ID格式错误：" + sessionIdString));
        }

        // ⭐ 会话归属校验：确认该会话属于当前用户，防止越权访问他人对话
        ConsultationSession session = consultationSessionService.getSessionByIdAndUserId(dbSessionId, userId);
        if (session == null) {
            return Flux.error(new BusinessException("会话不存在或无权访问"));
        }

        // 幂等保护：会话的首条消息已在 startSession 时保存，避免重复落库
        if (!isInitialMessage(dbSessionId, userMessage)) {
            consultationMessageService.saveUserMessage(dbSessionId, userMessage, null);
        }

        String conversationId = CONVERSATION_ID_PREFIX + sessionIdString;
        StringBuilder fullResponse = new StringBuilder();

        // ── 2. 组装模型流；对话记忆的读写交给 MessageChatMemoryAdvisor
        return chatClient.prompt()
                .system(PromptManage.PSYCHOLOGICAL_SUPPORT_SYSTEM_PROMPT)
                .user(userMessage)
                .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, conversationId))
                .stream()
                .content()
                .doOnNext(fullResponse::append)
                .doOnComplete(() -> consultationMessageService.saveAiMessage(
                        dbSessionId, fullResponse.toString(), MODEL_NAME));
    }

    /**
     * 判断本次消息是否为会话的初始消息（已在开启会话时保存过，需跳过重复落库）
     */
    private boolean isInitialMessage(Long dbSessionId, String userMessage) {
        Integer messageCount = consultationMessageService.getMessageCountBySessionId(dbSessionId);
        if (messageCount == null || messageCount != 1) {
            return false;
        }
        ConsultationMessageResponseDTO lastMessage =
                consultationMessageService.getLastMessageBySessionId(dbSessionId);
        return lastMessage != null
                && lastMessage.getSenderType() != null
                && lastMessage.getSenderType() == SENDER_TYPE_USER
                && userMessage != null
                && userMessage.equals(lastMessage.getContent());
    }

    /**
     * 从 session_x 形式的会话 ID 中解析出数据库主键；格式非法时返回 null
     */
    public Long extractSessionId(String sessionId) {
        if (sessionId == null || !sessionId.startsWith(SESSION_ID_PREFIX)) {
            return null;
        }
        try {
            return Long.parseLong(sessionId.substring(SESSION_ID_PREFIX.length()));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
