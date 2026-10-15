package com.rmh.aispringboot.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 开启 Spring 定时任务。
 *
 * <p>目前只有一个任务：清理过期的临时上传文件（{@code task/TempFileCleanupTask}）。
 * 加这个配置类而不是把 {@code @EnableScheduling} 放在启动类上，
 * 是为了让「应用入口」与「基础设施开关」分开，和 {@code WebMvcConfig} 等保持一致。
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
