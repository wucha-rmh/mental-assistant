package com.rmh.aispringboot.config;

import com.rmh.aispringboot.service.memory.RedisChatMemoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 对话链路装配：记忆仓库（内存 / Redis 二选一）+ 记忆窗口 + ChatClient。
 *
 * <p>窗口条数不在这里写死，统一由 {@link ChatMemoryProperties}（{@code chat.memory.max-messages}）
 * 提供，Redis 镜像的 LTRIM 与历史回灌条数也读同一个值，三处不会再漂移。
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(ChatMemoryProperties.class)
public class ChatClientConfig {

    /**
     * 默认走进程内存，保证 src/test 的 contextLoads 不需要 Redis 也能过；
     * 生产/联调用 CHAT_MEMORY_STORE=redis 打开 Redis 仓库。
     */
    @Bean
    @ConditionalOnProperty(name = "chat.memory.store", havingValue = "memory", matchIfMissing = true)
    public ChatMemoryRepository inMemoryChatMemoryRepository() {
        return new InMemoryChatMemoryRepository();
    }

    @Bean
    @ConditionalOnProperty(name = "chat.memory.store", havingValue = "redis")
    public ChatMemoryRepository redisChatMemoryRepository(
            StringRedisTemplate stringRedisTemplate,
            @Value("${app.redis.key-prefix:mha:}") String keyPrefix,
            ChatMemoryProperties chatMemoryProperties) {
        return new RedisChatMemoryRepository(
                stringRedisTemplate, keyPrefix, chatMemoryProperties.getMaxMessages());
    }

    @Bean
    public ChatMemory chatMemory(ChatMemoryRepository chatMemoryRepository,
                                 ChatMemoryProperties chatMemoryProperties) {
        int maxMessages = chatMemoryProperties.getMaxMessages();
        if (maxMessages <= 0) {
            throw new IllegalStateException(
                    "chat.memory.max-messages 必须大于 0，当前是 " + maxMessages
                            + "（application.yaml 里配置，或用环境变量 CHAT_MEMORY_MAX_MESSAGES 覆盖）");
        }
        log.info("对话记忆窗口上限 {} 条（chat.memory.max-messages，一问一答算 2 条）", maxMessages);
        return MessageWindowChatMemory.builder()
                .maxMessages(maxMessages)
                .chatMemoryRepository(chatMemoryRepository)
                .build();
    }

    @Bean("open-ai")
    public ChatClient chatClient(OpenAiChatModel openAiChatModel, ChatMemory chatMemory) {
        return ChatClient.builder(openAiChatModel)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }
}