package com.rmh.aispringboot.controller;

import com.rmh.aispringboot.DTO.response.FileUploadResponseDTO;
import com.rmh.aispringboot.common.Result;
import com.rmh.aispringboot.service.FileStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件上传（本地存储）
 *
 * <p>实际落盘逻辑在 {@link FileStorageService}（与用户头像上传共用），
 * 文件存在 {@code file.upload-dir}（默认 backend/uploads），
 * 对外通过 {@code /files/**} 匿名访问（见 {@code WebMvcConfig} 与 {@code SecurityConfig}）。
 *
 * <p>用户头像不走这里，见 {@code POST /api/user/avatar}。
 */
@RestController
@RequestMapping("/api/file")
public class FileController {

    @Autowired
    private FileStorageService fileStorageService;

    @PostMapping("/upload")
    public Result<FileUploadResponseDTO> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "businessType", required = false) String businessType,
            @RequestParam(value = "businessId", required = false) String businessId,
            @RequestParam(value = "businessField", required = false) String businessField) {
        return Result.ok(fileStorageService.store(file, businessType, businessId, businessField));
    }
}
