package com.rmh.aispringboot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 对话记忆窗口的配置（前缀 {@code chat.memory}）。
 *
 * <p>窗口上限只有这一个来源，两个使用方都读它：
 * <ol>
 *   <li>{@link ChatClientConfig} 构 {@code MessageWindowChatMemory} 时用它裁剪窗口；</li>
 *   <li>{@code PsychologicalSupportService} 从 {@code consultation_message} 回灌历史时按它取最近 N 条。</li>
 * </ol>
 *
 * <p>改这里就够了，不需要再动 Java 常量。也可以用环境变量覆盖（生产不必改包）：
 * {@code CHAT_MEMORY_MAX_MESSAGES=60}。
 */
@Data
@ConfigurationProperties(prefix = "chat.memory")
public class ChatMemoryProperties {

    /** 窗口最多保留的消息条数，一问一答算 2 条（30 条约等于 15 轮对话） */
    private int maxMessages = 30;
}
