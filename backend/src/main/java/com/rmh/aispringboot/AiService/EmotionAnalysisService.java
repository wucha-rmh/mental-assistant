package com.rmh.aispringboot.AiService;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.rmh.aispringboot.DTO.response.ConsultationMessageResponseDTO;
import com.rmh.aispringboot.DTO.response.SessionEmotionDTO;
import com.rmh.aispringboot.entity.ConsultationSession;
import com.rmh.aispringboot.exception.BusinessException;
import com.rmh.aispringboot.service.ConsultationMessageService;
import com.rmh.aispringboot.service.ConsultationSessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 会话情绪分析
 *
 * <p>把一段心理疏导对话交给大模型做结构化情绪评估，结果缓存到
 * {@code consultation_session.last_emotion_analysis}。缓存覆盖了会话的最后一条消息时直接复用，
 * 不再重复调用 AI；任何异常都退化为 {@link SessionEmotionDTO#defaultValue()}，保证前端情绪面板可用。
 */
@Slf4j
@Service
public class EmotionAnalysisService {

    /** 参与分析的最大消息条数（取最近若干条，控制 token 消耗） */
    private static final int MAX_ANALYSIS_MESSAGES = 20;

    /** 单条消息参与分析时的截断长度 */
    private static final int MAX_MESSAGE_LENGTH = 500;

    /** 改善建议最多保留条数 */
    private static final int MAX_IMPROVEMENT_SUGGESTIONS = 5;

    private static final int DEFAULT_EMOTION_SCORE = 50;
    private static final int MAX_EMOTION_SCORE = 100;
    private static final int MAX_RISK_LEVEL = 3;

    private static final String DEFAULT_EMOTION = "中性";

    /** 消息发送方：用户 */
    private static final int SENDER_TYPE_USER = 1;

    private final ChatClient chatClient;

    @Autowired
    private ConsultationSessionService consultationSessionService;

    @Autowired
    private ConsultationMessageService consultationMessageService;

    public EmotionAnalysisService(OpenAiChatModel openAiChatModel) {
        // 情绪分析是独立的一次性评估，不能带对话记忆 advisor，否则会污染疏导对话的上下文
        this.chatClient = ChatClient.builder(openAiChatModel).build();
    }

    /**
     * 取会话情绪：缓存仍有效则直接返回，否则调用 AI 分析并落库
     *
     * @param sessionId 会话主键
     * @param userId    当前登录用户 ID（校验归属）
     */
    public SessionEmotionDTO analyzeOrGet(Long sessionId, Long userId) {
        ConsultationSession session = consultationSessionService.getSessionByIdAndUserId(sessionId, userId);
        if (session == null) {
            throw new BusinessException("会话不存在或无权访问");
        }

        ConsultationMessageResponseDTO lastMessage =
                consultationMessageService.getLastMessageBySessionId(sessionId);
        if (lastMessage == null || lastMessage.getCreatedAt() == null) {
            // 还没有任何消息，不消耗 AI 调用
            return SessionEmotionDTO.defaultValue();
        }

        SessionEmotionDTO cached = parseCached(session.getLastEmotionAnalysis());
        if (cached != null && session.getLastEmotionUpdatedAt() != null
                && !lastMessage.getCreatedAt().isAfter(session.getLastEmotionUpdatedAt())) {
            // 上次分析已经覆盖了全部消息，直接复用
            return cached;
        }

        List<ConsultationMessageResponseDTO> messages =
                consultationMessageService.listMessagesBySessionId(sessionId);
        if (messages == null || messages.isEmpty()) {
            return SessionEmotionDTO.defaultValue();
        }

        SessionEmotionDTO analyzed = analyzeByAi(messages);
        if (analyzed == null) {
            // 分析失败不写缓存，下次请求可以重试
            return SessionEmotionDTO.defaultValue();
        }
        consultationSessionService.saveEmotionAnalysis(sessionId, JSONUtil.toJsonStr(analyzed));
        return analyzed;
    }

    /**
     * 解析会话上缓存的分析结果，缺失或格式非法返回 null
     */
    private SessionEmotionDTO parseCached(String raw) {
        if (StrUtil.isBlank(raw)) {
            return null;
        }
        try {
            return JSONUtil.toBean(raw, SessionEmotionDTO.class);
        } catch (Exception e) {
            log.warn("会话情绪缓存解析失败，将重新分析：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 调用大模型分析；任何异常都返回 null，由调用方退化为默认值
     */
    private SessionEmotionDTO analyzeByAi(List<ConsultationMessageResponseDTO> messages) {
        try {
            String transcript = buildTranscript(messages);
            String content = chatClient.prompt()
                    .system(PromptManage.EMOTION_ANALYSIS_SYSTEM_PROMPT)
                    .user(String.format(PromptManage.EMOTION_ANALYSIS_USER_PROMPT_TEMPLATE, transcript))
                    .call()
                    .content();
            return normalize(extractJson(content));
        } catch (Exception e) {
            log.warn("情绪分析调用失败，本次返回默认值：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 组装对话文本：只取最近若干条，单条过长时截断
     */
    private String buildTranscript(List<ConsultationMessageResponseDTO> messages) {
        int from = Math.max(0, messages.size() - MAX_ANALYSIS_MESSAGES);
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < messages.size(); i++) {
            ConsultationMessageResponseDTO message = messages.get(i);
            String content = message.getContent() == null ? "" : message.getContent().trim();
            if (content.isEmpty()) {
                continue;
            }
            if (content.length() > MAX_MESSAGE_LENGTH) {
                content = content.substring(0, MAX_MESSAGE_LENGTH) + "…";
            }
            boolean fromUser = message.getSenderType() != null && message.getSenderType() == SENDER_TYPE_USER;
            sb.append(fromUser ? "用户：" : "助手：").append(content).append('\n');
        }
        return sb.toString().trim();
    }

    /**
     * 从模型回复里取出 JSON 主体：忽略任何解释文字与 ``` 围栏，只截取最外层大括号之间的内容
     */
    private String extractJson(String content) {
        if (content == null) {
            return null;
        }
        String text = content.trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        return text.substring(start, end + 1);
    }

    /**
     * 字段校验与兜底：保证前端拿到的每个字段都可用（尤其 improvementSuggestions 不能为 null）
     */
    private SessionEmotionDTO normalize(String json) {
        if (StrUtil.isBlank(json)) {
            return null;
        }
        JSONObject object;
        try {
            object = JSONUtil.parseObj(json);
        } catch (Exception e) {
            log.warn("情绪分析结果不是合法 JSON：{}", e.getMessage());
            return null;
        }

        String primaryEmotion = object.getStr("primaryEmotion");
        if (StrUtil.isBlank(primaryEmotion)) {
            primaryEmotion = DEFAULT_EMOTION;
        }

        Integer emotionScore = object.getInt("emotionScore");
        if (emotionScore == null) {
            emotionScore = DEFAULT_EMOTION_SCORE;
        }
        emotionScore = Math.max(0, Math.min(MAX_EMOTION_SCORE, emotionScore));

        Integer riskLevel = object.getInt("riskLevel");
        if (riskLevel == null) {
            riskLevel = 0;
        }
        riskLevel = Math.max(0, Math.min(MAX_RISK_LEVEL, riskLevel));

        Boolean isNegative = object.getBool("isNegative");
        if (isNegative == null) {
            isNegative = riskLevel > 0;
        }

        List<String> improvementSuggestions = new ArrayList<>();
        Object rawSuggestions = object.get("improvementSuggestions");
        if (rawSuggestions instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (item == null) {
                    continue;
                }
                String text = item.toString().trim();
                if (!text.isEmpty()) {
                    improvementSuggestions.add(text);
                }
                if (improvementSuggestions.size() >= MAX_IMPROVEMENT_SUGGESTIONS) {
                    break;
                }
            }
        }

        return SessionEmotionDTO.builder()
                .primaryEmotion(primaryEmotion)
                .emotionScore(emotionScore)
                .isNegative(isNegative)
                .riskLevel(riskLevel)
                .suggestion(StrUtil.blankToDefault(object.getStr("suggestion"), ""))
                .improvementSuggestions(improvementSuggestions)
                .riskDescription(StrUtil.blankToDefault(object.getStr("riskDescription"), ""))
                .build();
    }
}
