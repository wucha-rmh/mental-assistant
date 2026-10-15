package com.rmh.aispringboot.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix="jwt")
public class JwtConfig {

    /** 密钥最短长度：短于此长度的 HMAC 密钥可以被离线暴力枚举 */
    private static final int MIN_SECRET_LENGTH = 32;

    private String secret;
    private Long expiration;
    private Long refreshExpiration;
    private String header;
    private String tokenPrefix;

    /**
     * 启动即校验密钥，留空或过短直接拒绝启动。
     *
     * <p>这里刻意「失败得响亮」：JWT 是纯签名校验（{@code JwtTokenUtil.verifyToken} 只验签名与 issuer），
     * 密钥等价于任意用户（含管理员）的登录凭证；配置缺失时若静默降级成空密钥，
     * 等于把整个后台交出去，所以宁可不启动。
     */
    @PostConstruct
    void validateSecret() {
        if (secret == null || secret.trim().length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "jwt.secret 未配置或长度不足 " + MIN_SECRET_LENGTH + " 位。"
                            + "本机请在 backend/src/main/resources/application-local.yaml 里配置 jwt.secret；"
                            + "生产请设置环境变量 JWT_SECRET（建议 48 位以上随机串，可用 `openssl rand -base64 48` 生成）。"
                            + "注意旧值已随 git 历史泄露，绝不要复用。");
        }
    }
}
