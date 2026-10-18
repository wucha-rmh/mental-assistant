package com.rmh.aispringboot.common;

/**
 * 对话记忆的 conversationId 唯一出处。
 *
 * <p>记忆窗口的 key、数据库回灌、删除会话时的清理必须用同一个 id，
 * 否则会出现「同一个会话两个记忆窗口」或「删了会话记忆还在」。
 */
public final class ConversationIds {

    public static final String CONVERSATION_PREFIX = "conversation_";

    private ConversationIds() {
    }

    /** 用数据库主键生成，格式固定 conversation_session_45，与入参写法无关。 */
    public static String ofSessionId(Long sessionId) {
        return CONVERSATION_PREFIX + "session_" + sessionId;
    }
}