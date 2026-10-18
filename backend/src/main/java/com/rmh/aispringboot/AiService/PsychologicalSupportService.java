package com.rmh.aispringboot.AiService;

import com.rmh.aispringboot.DTO.command.ConsultationSessionCreateDTO;
import com.rmh.aispringboot.DTO.response.ConsultationMessageResponseDTO;
import com.rmh.aispringboot.common.ConversationIds;
import com.rmh.aispringboot.config.ChatMemoryProperties;
import com.rmh.aispringboot.entity.ConsultationSession;
import com.rmh.aispringboot.exception.BusinessException;
import com.rmh.aispringboot.service.ConsultationMessageService;
import com.rmh.aispringboot.service.ConsultationSessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/**
 * 心理疏导对话服务
 *
 * <p>对话记忆（ChatMemory）的增量读写交由 MessageChatMemoryAdvisor 自动管理，
 * 本类负责四件事：前置校验、消息落库、会话首次访问时从数据库回灌历史记忆、
 * 把模型输出流原样转发出去。
 *
 * <p>记忆窗口由 {@link ChatMemoryProperties}（{@code chat.memory.max-messages}）决定大小，
 * 记忆只存在进程内（{@code MessageWindowChatMemory}）。进程重启后窗口为空，某个会话第一次被
 * 访问时用 consultation_message 回灌最近 N 条，所以 MySQL 是唯一真相源。
 */
@Slf4j
@Service
public class PsychologicalSupportService {

    /** 业务会话 ID 前缀，形如 session_1 */
    private static final String SESSION_ID_PREFIX = "session_";
    /** 对话记忆标识前缀，形如 conversation_session_1 */
    //private static final String CONVERSATION_ID_PREFIX = "conversation_";
    /** 会话有效期：24 小时 */
    private static final long SESSION_TTL_MILLIS = 86_400_000L;
    /** 会话初始状态 */
    private static final String SESSION_STATUS_ACTIVE = "ACTIVE";
    /** 消息发送者类型：用户 */
    private static final int SENDER_TYPE_USER = 1;
    /** 消息发送者类型：AI */
    private static final int SENDER_TYPE_AI = 2;
    /** 模型标识，落库时记录 */
    private static final String MODEL_NAME = "openai";

    @Autowired
    @Qualifier("open-ai")
    private ChatClient chatClient;

    /** 记忆窗口上限（chat.memory.max-messages），窗口装配与历史回灌共用 */
    @Autowired
    private ChatMemoryProperties chatMemoryProperties;

    /**
     * 与 MessageChatMemoryAdvisor 共用的记忆窗口，回灌历史时直接写它
     */
    @Autowired
    private ChatMemory chatMemory;

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
        consultationMessageService.saveUserMessage(session.getId(), createDTO.getInitialMessage());

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
            consultationMessageService.saveUserMessage(dbSessionId, userMessage);
        }
        // 原来：String conversationId = CONVERSATION_ID_PREFIX + sessionIdString;
        String conversationId = ConversationIds.ofSessionId(dbSessionId);
        StringBuilder fullResponse = new StringBuilder();

        // ⭐ 后端重启后记忆窗口是空的，先用数据库里的历史回灌，避免 AI「失忆」
        ensureMemoryLoaded(dbSessionId, conversationId, userMessage);

        // ── 2. 组装模型流；对话记忆的增量读写交给 MessageChatMemoryAdvisor
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
     * 从数据库回灌对话记忆（只在会话首次被访问时执行一次）。
     *
     * <p>MessageChatMemoryAdvisor 用的 MessageWindowChatMemory 只活在进程内存里，
     * 后端一重启就全空，AI 会「失忆」。这里在会话第一次被访问时，把
     * consultation_message 里最近的「chat.memory.max-messages」条历史塞回记忆窗口，
     * 之后仍由 advisor 正常增量维护。已经在内存里的活跃会话直接跳过。
     *
     * <p>注意：本次用户消息可能已经落库（见 {@link #isInitialMessage}），
     * 它随后会被 advisor 作为当前提问再追加一次，所以要从历史里剔除，否则模型会看到重复的一句。
     *
     * @param dbSessionId        数据库会话主键
     * @param conversationId     记忆窗口的会话标识，形如 conversation_session_1
     * @param currentUserMessage 用户本次发送的消息
     */
    private void ensureMemoryLoaded(Long dbSessionId, String conversationId, String currentUserMessage) {
        List<Message> loaded = chatMemory.get(conversationId);
        if (loaded != null && !loaded.isEmpty()) {
            return;
        }
        try {
            List<ConsultationMessageResponseDTO> history =
                    consultationMessageService.listMessagesBySessionId(dbSessionId);
            if (history == null || history.isEmpty()) {
                return;
            }

            int end = history.size();
            ConsultationMessageResponseDTO lastMessage = history.get(end - 1);
            if (lastMessage.getSenderType() != null
                    && lastMessage.getSenderType() == SENDER_TYPE_USER
                    && currentUserMessage != null
                    && currentUserMessage.equals(lastMessage.getContent())) {
                end--;
            }

            int windowMessages = chatMemoryProperties.getMaxMessages();
            int start = Math.max(0, end - windowMessages);
            List<Message> messages = new ArrayList<>(end - start);
            for (int i = start; i < end; i++) {
                ConsultationMessageResponseDTO item = history.get(i);
                if (item.getContent() == null || item.getContent().isBlank()) {
                    continue;
                }
                if (item.getSenderType() != null && item.getSenderType() == SENDER_TYPE_AI) {
                    messages.add(new AssistantMessage(item.getContent()));
                } else {
                    messages.add(new UserMessage(item.getContent()));
                }
            }
            // 历史被窗口截断时，首条可能是 AI 回复；丢掉它，保证上下文以用户消息开头
            if (!messages.isEmpty() && messages.get(0) instanceof AssistantMessage) {
                messages.remove(0);
            }
            if (messages.isEmpty()) {
                return;
            }
            chatMemory.add(conversationId, messages);
            log.info("回灌对话记忆 sessionId={} conversationId={} messages={}",
                    dbSessionId, conversationId, messages.size());
        } catch (Exception e) {
            // 回灌失败不影响本次对话，只是这轮 AI 看不到历史
            log.warn("回灌对话记忆失败 sessionId={}：{}", dbSessionId, e.getMessage());
        }
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
