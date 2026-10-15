package com.rmh.aispringboot.DTO.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件上传响应
 *
 * <p>前端只用 {@code filePath}（相对路径，形如 /files/bussiness/article/xxx.png），
 * 拼上文件访问前缀即可直接访问，不再需要外部文件服务器。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FileUploadResponseDTO {

    // 文件记录ID
    private Long id;

    // 原始文件名
    private String originalName;

    // 相对访问路径
    private String filePath;

    // 文件大小（字节）
    private Long fileSize;

    // 文件类型：IMG / VIDEO / DOC
    private String fileType;

    // 业务类型
    private String businessType;

    // 业务ID
    private String businessId;

    // 业务字段
    private String businessField;
}
