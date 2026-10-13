package com.rmh.aispringboot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix="jwt")
public class JwtConfig {

    private String secret;
    private Long expiration;
    private Long refreshExpiration;
    private String header;
    private String tokenPrefix;
}
