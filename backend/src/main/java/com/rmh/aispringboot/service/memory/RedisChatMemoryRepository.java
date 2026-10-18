package com.rmh.aispringboot.service.memory;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 对话记忆窗口：进程内持有权威副本，Redis 做持久化镜像与跨重启预热。
 * key 形如 {@code mha:chat:conversation_session_45}（LIST，元素 {@code u|/a|/s|} 文本前缀）。
 *
 * <p>为什么窗口必须留在进程内，而不是「Redis 就是窗口」：
 * {@link org.springframework.ai.chat.memory.MessageWindowChatMemory#add} 是<b>读-改-写</b>语义
 * （先 {@code findByConversationId}、再 {@code saveAll}）。如果直接拿 Redis 当窗口，
 * 一次网络抖动让读失败返回空，紧接着的 {@code saveAll} 就会用「只有当前这条」覆盖整个窗口，
 * 历史被静默截断 —— 实测过一次：断连期间一轮对话后，窗口从 5 条变成 2 条。
 * 窗口放进程内后，读永不失败；写失败只是这一轮没同步到 Redis，下一轮成功时会把完整窗口整体重写，自愈。
 *
 * <p>三条必须知道的前提：
 * <ol>
 *   <li>{@code saveAll} 是<b>全量覆盖</b>语义（advisor 每轮把整个窗口写回），所以写 Redis 必须
 *       DEL + RPUSH + LTRIM + EXPIRE 放在一个事务里，不能直接 RPUSH。</li>
 *   <li>任何 Redis 异常都<b>不能往上抛</b>：读失败只影响「预热」（service 会回灌数据库），
 *       写失败只记日志。</li>
 *   <li>冷启动预热每个会话只做一次（{@code warmed}），避免 Redis 里没有该 key 时每轮都打网络。</li>
 * </ol>
 *
 * <p>已知局限：
 * <ul>
 *   <li>窗口副本只在本进程。多实例部署时各实例各自持有窗口，需要额外的一致性方案（粘性会话或共享存储）。</li>
 *   <li>如果某个会话的最后一次写 Redis 就失败了、紧接着进程重启，Redis 里留下的是旧窗口，
 *       重启后会预热出「少一条」的记忆（数据库里是全的）。要彻底消除得拿数据库条数做对账。</li>
 * </ul>
 */
@Slf4j
public class RedisChatMemoryRepository implements ChatMemoryRepository {

    private static final String CHAT_KEY_INFIX = "chat:";
    private static final String SEPARATOR = "|";
    /** 7 天滑动：用户一周没来的会话记忆自然过期，避免长期占用内存 */
    private static final Duration TTL = Duration.ofDays(7);

    /** 进程内窗口（权威副本），key 是 conversationId */
    private final Map<String, List<Message>> windowCache = new ConcurrentHashMap<>();
    /** 已经从 Redis 预热过的会话：Redis 里没有该 key 时不要每轮都去问 */
    private final Set<String> warmed = ConcurrentHashMap.newKeySet();

    private final StringRedisTemplate redis;
    private final String keyPrefix;
    /** 镜像最多保留的条数，由 chat.memory.max-messages 注入，必须与记忆窗口上限一致 */
    private final int maxMessages;

    public RedisChatMemoryRepository(StringRedisTemplate redis, String keyPrefix, int maxMessages) {
        this.redis = redis;
        this.keyPrefix = keyPrefix;
        this.maxMessages = maxMessages > 0 ? maxMessages : 30;
    }

    @Override
    public List<String> findConversationIds() {
        Set<String> ids = new LinkedHashSet<>(windowCache.keySet());
        String keyPrefixWithInfix = keyPrefix + CHAT_KEY_INFIX;
        String pattern = keyPrefixWithInfix + "*";
        try {
            // 必须用 SCAN，不能用 KEYS：KEYS 会阻塞整个 Redis
            redis.execute((RedisCallback<Void>) connection -> {
                ScanOptions options = ScanOptions.scanOptions().match(pattern).count(500).build();
                try (Cursor<byte[]> cursor = connection.keyCommands().scan(options)) {
                    while (cursor.hasNext()) {
                        String key = new String(cursor.next(), StandardCharsets.UTF_8);
                        ids.add(key.substring(keyPrefixWithInfix.length()));
                    }
                }
                return null;
            });
        } catch (Exception e) {
            log.warn("扫描 Redis 对话记忆 key 失败：{}", e.getMessage());
        }
        return new ArrayList<>(ids);
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return List.of();
        }
        List<Message> cached = windowCache.get(conversationId);
        if (cached != null) {
            return cached;
        }
        if (!warmed.add(conversationId)) {
            // 预热过且 Redis 里确实没有：直接返回空，由 service 决定是否回灌数据库
            return List.of();
        }
        List<Message> fromRedis = loadFromRedis(conversationId);
        if (!fromRedis.isEmpty()) {
            windowCache.put(conversationId, fromRedis);
        }
        return fromRedis;
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        if (conversationId == null || conversationId.isBlank()) {
            return;
        }
        // 内存先更新：即使 Redis 写失败，后续轮次的「读-改-写」也基于完整窗口，不会截断历史
        List<Message> snapshot = messages == null ? List.of() : List.copyOf(messages);
        windowCache.put(conversationId, snapshot);
        warmed.add(conversationId);
        writeToRedis(conversationId, snapshot);
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return;
        }
        windowCache.remove(conversationId);
        warmed.remove(conversationId);
        try {
            redis.delete(key(conversationId));
        } catch (Exception e) {
            log.warn("删除 Redis 对话记忆失败 conversationId={}：{}", conversationId, e.getMessage());
        }
    }

    private List<Message> loadFromRedis(String conversationId) {
        try {
            List<String> raw = redis.opsForList().range(key(conversationId), 0, -1);
            if (raw == null || raw.isEmpty()) {
                return List.of();
            }
            List<Message> messages = new ArrayList<>(raw.size());
            for (String item : raw) {
                int sep = item.indexOf(SEPARATOR);
                if (sep <= 0) {
                    continue;
                }
                String text = item.substring(sep + 1);
                if (text.isBlank()) {
                    continue;
                }
                switch (item.charAt(0)) {
                    case 'u' -> messages.add(new UserMessage(text));
                    case 'a' -> messages.add(new AssistantMessage(text));
                    case 's' -> messages.add(new SystemMessage(text));
                    default -> { /* 未知前缀，跳过 */ }
                }
            }
            return List.copyOf(messages);
        } catch (Exception e) {
            // 预热失败 = 当作没有记忆：service 会用数据库回灌兜底
            log.warn("读取 Redis 对话记忆失败 conversationId={}：{}", conversationId, e.getMessage());
            return List.of();
        }
    }

    private void writeToRedis(String conversationId, List<Message> messages) {
        String key = key(conversationId);
        List<String> encoded = new ArrayList<>(messages.size());
        for (Message message : messages) {
            String text = message.getText();
            if (text == null || text.isBlank()) {
                continue;
            }
            encoded.add(prefixOf(message.getMessageType()) + SEPARATOR + text);
        }
        try {
            redis.execute(new SessionCallback<Object>() {
                @Override
                @SuppressWarnings({"unchecked", "rawtypes"})
                public Object execute(RedisOperations operations) {
                    operations.multi();
                    operations.delete(key);
                    if (!encoded.isEmpty()) {
                        operations.opsForList().rightPushAll(key, encoded);
                        operations.opsForList().trim(key, -maxMessages, -1);
                    }
                    operations.expire(key, TTL);
                    return operations.exec();
                }
            });
        } catch (Exception e) {
            // 只影响持久化镜像；进程内窗口仍是完整的，下一轮写成功会整体重写
            log.warn("写入 Redis 对话记忆失败 conversationId={}：{}", conversationId, e.getMessage());
        }
    }

    private String key(String conversationId) {
        return keyPrefix + CHAT_KEY_INFIX + conversationId;
    }

    private String prefixOf(MessageType type) {
        if (type == MessageType.ASSISTANT) {
            return "a";
        }
        if (type == MessageType.SYSTEM) {
            return "s";
        }
        return "u";
    }
}
