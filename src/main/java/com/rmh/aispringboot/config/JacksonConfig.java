package com.rmh.aispringboot.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ext.javatime.ser.LocalDateTimeSerializer;
import tools.jackson.databind.module.SimpleModule;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * JSON 日期时间格式统一为 {@code yyyy-MM-dd HH:mm:ss}。
 *
 * <p>背景：Spring Boot 默认把 {@code LocalDateTime} 序列化成 ISO-8601
 * （{@code 2026-10-15T15:59:46}，有毫秒时是 {@code 2026-10-15T15:59:46.923}），
 * 而前端（{@code consultations.vue}、{@code consultation.vue}、{@code emotional.vue}）
 * 是直接把返回值渲染出来的，页面上就会出现带 {@code T}、带毫秒的时间。
 *
 * <p>⚠️ 两个坑：
 * <ol>
 *   <li>{@code spring.jackson.date-format} 只对 {@code java.util.Date}/{@code Calendar} 生效，
 *       对 JSR-310 的 java.time 类型**无效**——本项目响应 DTO 里全是 {@code LocalDateTime}，
 *       所以必须显式注册序列化器。</li>
 *   <li>本项目是 Spring Boot 4，底层已经是 **Jackson 3**（包名从 {@code com.fasterxml.jackson}
 *       变成 {@code tools.jackson}），所以扩展点是 {@link JsonMapperBuilderCustomizer}
 *       而不是 Jackson 2 时代的 {@code Jackson2ObjectMapperBuilderCustomizer}。</li>
 * </ol>
 *
 * <p>反序列化不动：客户端没有任何 LocalDateTime 入参（日期入参都是 {@code String}，
 * 见 {@code EmotionDiaryService.parseDiaryDate}），不需要放宽解析规则。
 */
@Configuration
public class JacksonConfig {

    /** 2026-10-15 15:59:46 */
    private static final DateTimeFormatter LOCAL_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Bean
    public JsonMapperBuilderCustomizer localDateTimeFormatCustomizer() {
        return builder -> {
            SimpleModule module = new SimpleModule("mental-health-date-time");
            module.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(LOCAL_DATE_TIME));
            builder.addModule(module);
        };
    }
}
