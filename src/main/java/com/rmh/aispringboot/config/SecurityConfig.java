package com.rmh.aispringboot.config;

import cn.hutool.core.text.AntPathMatcher;
import com.rmh.aispringboot.util.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final AntPathMatcher antPathMatcher = new AntPathMatcher();
    private static final String[] PUBLIC_PATH = {
            "/",
            "/api/test",
            "/api/user/login",
            "/api/user/register"
    };
    //判断是否在公开路径中
    public  static boolean isPublicPATH(String requestUrl){
        for (String path : PUBLIC_PATH) {
            if (antPathMatcher.match(path, requestUrl)) {
                return true;
            }
        }
        return false;
    }
    @Bean
    public JwtAuthenticationFilter jwtAuthticationFilter(){
        return new JwtAuthenticationFilter();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                //禁用CSRF保护(API服务不需要)
                .csrf(AbstractHttpConfigurer::disable)
                //配置会话管理为无状态（JWT需要）
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                //配置请求的授权规则
                .authorizeHttpRequests( auth->auth
                        //公开路径，无需登录即可访问
                        .requestMatchers(PUBLIC_PATH).permitAll()
                        .anyRequest().authenticated()
                )
                //添加JWT认证过滤器
                .addFilterBefore(jwtAuthticationFilter(),UsernamePasswordAuthenticationFilter.class);
    return http.build();

    }
}
