package com.rmh.aispringboot.config;

import com.rmh.aispringboot.service.memory.RedisChatMemoryRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class ChatClientConfig {

    /** 记忆窗口上限：改这里要同步改 RedisChatMemoryRepository.MAX_MESSAGES 与 MEMORY_WINDOW_MESSAGES */
    private static final int MAX_MESSAGES = 30;

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
            @Value("${app.redis.key-prefix:mha:}") String keyPrefix) {
        return new RedisChatMemoryRepository(stringRedisTemplate, keyPrefix);
    }

    @Bean
    public ChatMemory chatMemory(ChatMemoryRepository chatMemoryRepository) {
        return MessageWindowChatMemory.builder()
                .maxMessages(MAX_MESSAGES)
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