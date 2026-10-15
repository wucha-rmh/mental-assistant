package com.rmh.aispringboot.service;

import com.rmh.aispringboot.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录失败限流器（进程内实现）。
 *
 * <p>对应「登录接口公开且无限流」这个缺陷，作用有三：
 * ① 挡住对单个账号的密码爆破——连续 {@link #MAX_FAILURES} 次失败后锁定 {@link #LOCK_DURATION}；
 * ② 抬高撞库成本——BCrypt 每次比对约 90ms，不限流时几百个并发就能把 CPU 与线程打满；
 * ③ 让试错不再免费，和 Nginx 的 {@code limit_req} 形成两层（应用层按账号，接入层按来源 IP）。
 *
 * <p>键是「用户名 + 客户端 IP」。IP 只取 {@code request.getRemoteAddr()}，
 * <b>刻意不信任 {@code X-Forwarded-For}</b>：请求头谁都能伪造，信它等于给攻击者一个「换 IP 就重置计数」的开关。
 * 部署在 Nginx 后面时 remoteAddr 是 127.0.0.1，实际效果就是按账号限流——这已经能挡住针对某个账号的爆破；
 * 按来源 IP 的限流交给 Nginx 那一层（见 {@code deploy/nginx.conf.example}）。
 *
 * <p>单实例内存实现：重启即清空，多实例部署时要换成 Redis 之类的共享存储。
 */
@Slf4j
@Component
public class LoginAttemptLimiter {

    /** 连续失败多少次后锁定 */
    private static final int MAX_FAILURES = 5;
    /** 失败计数窗口：距最后一次失败超过这么久就重新计数 */
    private static final Duration FAILURE_WINDOW = Duration.ofMinutes(15);
    /** 锁定时长 */
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    /** 最多跟踪多少个键；超过后先清理过期项，防止被随机用户名刷爆内存 */
    private static final int MAX_TRACKED_KEYS = 10_000;

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    /**
     * 是否允许继续尝试登录；处于锁定中时直接抛业务异常（提示剩余分钟数）
     */
    public void checkAllowed(String username) {
        String key = key(username);
        Attempt attempt = attempts.get(key);
        if (attempt == null) {
            return;
        }
        Instant now = Instant.now();
        if (attempt.isLocked(now)) {
            long minutes = Math.max(1, Duration.between(now, attempt.lockedUntil).toMinutes() + 1);
            throw new BusinessException("登录失败次数过多，账号已被临时锁定，请 " + minutes + " 分钟后再试");
        }
        if (attempt.isExpired(now)) {
            attempts.remove(key);
        }
    }

    /**
     * 记一次登录失败；达到阈值时开始锁定
     */
    public void recordFailure(String username) {
        purgeExpiredIfTooMany();
        String key = key(username);
        attempts.compute(key, (k, old) -> {
            Instant now = Instant.now();
            Attempt attempt = (old == null || old.isExpired(now)) ? new Attempt() : old;
            attempt.failures++;
            attempt.lastFailureAt = now;
            if (attempt.failures >= MAX_FAILURES) {
                attempt.lockedUntil = now.plus(LOCK_DURATION);
                log.warn("登录失败已达 {} 次，临时锁定 {} 分钟：{}", attempt.failures,
                        LOCK_DURATION.toMinutes(), key);
            }
            return attempt;
        });
    }

    /**
     * 登录成功：清空该账号的失败计数
     */
    public void reset(String username) {
        attempts.remove(key(username));
    }

    private void purgeExpiredIfTooMany() {
        if (attempts.size() < MAX_TRACKED_KEYS) {
            return;
        }
        Instant now = Instant.now();
        attempts.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
    }

    private static String key(String username) {
        String normalized = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        return normalized + "|" + clientIp();
    }

    private static String clientIp() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return "unknown";
        }
        HttpServletRequest request = attributes.getRequest();
        String remoteAddr = request.getRemoteAddr();
        return StringUtils.hasText(remoteAddr) ? remoteAddr : "unknown";
    }

    /** 单个「用户名 + IP」的失败记录 */
    private static final class Attempt {
        private int failures;
        private Instant lastFailureAt;
        private Instant lockedUntil;

        boolean isLocked(Instant now) {
            return lockedUntil != null && now.isBefore(lockedUntil);
        }

        boolean isExpired(Instant now) {
            if (isLocked(now)) {
                return false;
            }
            return lastFailureAt == null
                    || Duration.between(lastFailureAt, now).compareTo(FAILURE_WINDOW) > 0;
        }
    }
}
