package com.rmh.aispringboot.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 对话链路装配：记忆窗口 + ChatClient。
 *
 * <p>对话记忆只放在进程内存里（{@link MessageWindowChatMemory} 默认的
 * {@code InMemoryChatMemoryRepository}）：重启后窗口为空，由
 * {@code PsychologicalSupportService.ensureMemoryLoaded(...)} 从
 * {@code consultation_message} 回灌，MySQL 是唯一真相源。
 *
 * <p>窗口条数不在这里写死，统一由 {@link ChatMemoryProperties}（{@code chat.memory.max-messages}）
 * 提供，回灌条数也读同一个值，两处不会再漂移。
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(ChatMemoryProperties.class)
public class ChatClientConfig {

    @Bean
    public ChatMemory chatMemory(ChatMemoryProperties chatMemoryProperties) {
        int maxMessages = chatMemoryProperties.getMaxMessages();
        if (maxMessages <= 0) {
            throw new IllegalStateException(
                    "chat.memory.max-messages 必须大于 0，当前是 " + maxMessages
                            + "（application.yaml 里配置，或用环境变量 CHAT_MEMORY_MAX_MESSAGES 覆盖）");
        }
        log.info("对话记忆窗口上限 {} 条（chat.memory.max-messages，一问一答算 2 条）", maxMessages);
        return MessageWindowChatMemory.builder()
                .maxMessages(maxMessages)
                .build();
    }

    @Bean("open-ai")
    public ChatClient chatClient(OpenAiChatModel openAiChatModel, ChatMemory chatMemory) {
        return ChatClient.builder(openAiChatModel)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }
}
