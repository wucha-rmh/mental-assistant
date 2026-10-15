package com.rmh.aispringboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rmh.aispringboot.DTO.response.FileUploadResponseDTO;
import com.rmh.aispringboot.entity.SysFileInfo;
import com.rmh.aispringboot.exception.BusinessException;
import com.rmh.aispringboot.mapper.SysFileInfoMapper;
import com.rmh.aispringboot.util.SecurityUserUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 本地文件存储（项目内自给自足，不依赖任何外部文件服务器）
 *
 * <p>文件落盘到 {@code file.upload-dir}（默认 {@code ./uploads}，即 backend/uploads），
 * 数据库 {@code sys_file_info} 只记录相对路径 {@code /files/...}，
 * 由 {@code WebMvcConfig} 把 {@code /files/**} 映射到该目录。
 *
 * <p>文章封面上传（{@code FileController}）与用户头像上传（{@code UserService}）
 * 共用这一份实现，避免两处各写一套落盘规则。
 */
@Service
public class FileStorageService {

    /** 业务类型：文章（封面） */
    public static final String BUSINESS_TYPE_ARTICLE = "ARTICLE";
    /** 业务类型：用户头像 */
    public static final String BUSINESS_TYPE_AVATAR = "USER_AVATAR";
    /** 文件类型：图片 */
    public static final String FILE_TYPE_IMAGE = "IMG";
    /** 正式文件子目录 */
    private static final String DIR_ARTICLE = "bussiness/article";
    private static final String DIR_AVATAR = "bussiness/user_avatar";
    /** 未识别业务类型的临时文件子目录 */
    private static final String DIR_TEMP = "temp";
    /** 对外访问路径前缀 */
    public static final String URL_PREFIX = "/files/";
    /** 头像文件的路径前缀（换头像时只清理这个前缀下的旧文件） */
    public static final String AVATAR_URL_PREFIX = URL_PREFIX + DIR_AVATAR + "/";
    /** 临时文件保留时长（小时） */
    private static final long TEMP_EXPIRE_HOURS = 24L;

    private static final Set<String> IMAGE_EXTENSIONS =
            Set.of("jpg", "jpeg", "png", "gif", "bmp", "webp", "svg");
    private static final Set<String> VIDEO_EXTENSIONS =
            Set.of("mp4", "avi", "mov", "wmv", "flv", "mkv");

    @Autowired
    private SysFileInfoMapper sysFileInfoMapper;

    /** 文件落盘根目录（相对后端工作目录） */
    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    /**
     * 保存上传的文件并写入 {@code sys_file_info}。
     *
     * @param businessType ARTICLE / USER_AVATAR / 其它（其它一律按临时文件处理）
     * @return 含 {@code filePath}（形如 {@code /files/bussiness/article/xxx.png}）的响应
     */
    public FileUploadResponseDTO store(MultipartFile file, String businessType, String businessId, String businessField) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }

        String type = normalizeBusinessType(businessType);
        boolean temp = !BUSINESS_TYPE_ARTICLE.equals(type) && !BUSINESS_TYPE_AVATAR.equals(type);
        String subDir = temp ? DIR_TEMP : (BUSINESS_TYPE_AVATAR.equals(type) ? DIR_AVATAR : DIR_ARTICLE);

        String originalName = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String extension = extractExtension(originalName);
        String storedName = System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8)
                + (extension.isEmpty() ? "" : "." + extension);

        Path targetDir = uploadRoot().resolve(subDir);
        Path target = targetDir.resolve(storedName);
        try {
            Files.createDirectories(targetDir);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target);
            }
        } catch (IOException e) {
            throw new BusinessException("文件保存失败：" + e.getMessage());
        }

        LocalDateTime now = LocalDateTime.now();
        SysFileInfo record = SysFileInfo.builder()
                .originalName(originalName)
                .filePath(URL_PREFIX + subDir + "/" + storedName)
                .fileSize(file.getSize())
                .fileType(resolveFileType(file.getContentType(), extension))
                .businessType(type.isEmpty() ? null : type)
                .businessId(blankToNull(businessId))
                .businessField(blankToNull(businessField))
                .uploadUserId(currentUserIdOrNull())
                .isTemp(temp ? 1 : 0)
                .status(temp ? 0 : 1)
                .createTime(now)
                .expireTime(temp ? now.plusHours(TEMP_EXPIRE_HOURS) : null)
                .build();
        sysFileInfoMapper.insert(record);

        return FileUploadResponseDTO.builder()
                .id(record.getId())
                .originalName(record.getOriginalName())
                .filePath(record.getFilePath())
                .fileSize(record.getFileSize())
                .fileType(record.getFileType())
                .businessType(record.getBusinessType())
                .businessId(record.getBusinessId())
                .businessField(record.getBusinessField())
                .build();
    }

    /**
     * 删除本地上传的文件与对应的 {@code sys_file_info} 记录。
     *
     * <p>只处理 {@code /files/} 前缀的路径，且必须落在 {@code file.upload-dir} 内（防目录穿越）；
     * 失败不抛异常——清理旧文件不应影响主流程。
     */
    public void deleteStoredFile(String filePath) {
        if (filePath == null || !filePath.startsWith(URL_PREFIX)) {
            return;
        }
        String relative = filePath.substring(URL_PREFIX.length());
        Path root = uploadRoot();
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root)) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            // 文件被占用或已被删除：忽略
        }
        sysFileInfoMapper.delete(new LambdaQueryWrapper<SysFileInfo>().eq(SysFileInfo::getFilePath, filePath));
    }

    /** 落盘根目录的绝对路径 */
    private Path uploadRoot() {
        return Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    /** 业务类型统一大写；为空表示未指定 */
    private static String normalizeBusinessType(String businessType) {
        return businessType == null ? "" : businessType.trim().toUpperCase(Locale.ROOT);
    }

    /** 取扩展名（只保留字母数字，避免路径穿越与奇怪文件名） */
    private static String extractExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /** 文件类型：图片 / 视频 / 其它 */
    private static String resolveFileType(String contentType, String extension) {
        String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (type.startsWith("image/") || IMAGE_EXTENSIONS.contains(extension)) {
            return FILE_TYPE_IMAGE;
        }
        if (type.startsWith("video/") || VIDEO_EXTENSIONS.contains(extension)) {
            return "VIDEO";
        }
        return "DOC";
    }

    /** 前端可能把 undefined 序列化成字符串 "null"，这里统一成真正的 null */
    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || "null".equalsIgnoreCase(trimmed) || "undefined".equalsIgnoreCase(trimmed)) {
            return null;
        }
        return trimmed;
    }

    /** 取当前登录用户ID；取不到时不影响上传 */
    private Long currentUserIdOrNull() {
        try {
            return SecurityUserUtil.currentUser().getId();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
