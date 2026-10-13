package com.rmh.aispringboot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_PATH = {
            "/",
            "/api/test",
            "/api/user/login"
    };
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                //禁用CSRF保护(API服务不需要)
                .csrf(AbstractHttpConfigurer::disable)
                //配置会话管理为无状态（JWT需要）
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                //配置求情的授权规则
                .authorizeHttpRequests( auth->auth
                        //公开路径，无需登录即可访问
                        .requestMatchers(PUBLIC_PATH).permitAll()
                        .anyRequest().authenticated()
                );
    return http.build();

    }
}
