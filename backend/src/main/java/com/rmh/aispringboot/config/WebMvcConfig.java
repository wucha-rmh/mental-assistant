package com.rmh.aispringboot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 静态资源映射
 *
 * <p>把本地上传目录（{@code file.upload-dir}，默认 {@code ./uploads}）挂到 {@code /files/**}，
 * 这样数据库里存的相对路径 {@code /files/bussiness/article/xxx.png} 可以直接访问，
 * 前端不再需要任何外部文件服务器/图床。
 *
 * <p>注意：{@code /files/**} 已加入 {@code SecurityConfig.PUBLIC_PATH}，
 * 否则 {@code <img src>} 无法携带 token，图片会被 401 拦掉。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /** 文件落盘根目录，与 FileController 保持一致 */
    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException ignored) {
            // 目录建不出来不阻塞启动，真正的错误会在上传时暴露
        }
        String location = root.toUri().toString();
        if (!location.endsWith("/")) {
            location = location + "/";
        }
        registry.addResourceHandler("/files/**").addResourceLocations(location);
    }
}
