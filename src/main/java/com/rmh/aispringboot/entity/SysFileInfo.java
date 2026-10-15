package com.rmh.aispringboot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文件信息（本地文件存储的元数据）
 *
 * <p>文件实体落盘到项目内的 uploads 目录（见 file.upload-dir 配置），
 * 数据库只保存相对路径，形如 {@code /files/bussiness/article/1756963989972.png}，
 * 由 WebMvcConfig 把 {@code /files/**} 映射到该目录对外提供，不依赖任何外部文件服务器。
 */
@Data
@TableName("sys_file_info")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SysFileInfo {

    // 主键（自增）
    @TableId(type = IdType.AUTO)
    private Long id;

    // 上传时的原始文件名
    @TableField("original_name")
    private String originalName;

    // 相对访问路径，以 /files 开头
    @TableField("file_path")
    private String filePath;

    // 文件大小（字节）
    @TableField("file_size")
    private Long fileSize;

    // 文件类型：IMG / VIDEO / DOC
    @TableField("file_type")
    private String fileType;

    // 业务类型：ARTICLE / USER_AVATAR
    @TableField("business_type")
    private String businessType;

    // 业务ID（文章封面用前端生成的 UUID，头像用用户ID）
    @TableField("business_id")
    private String businessId;

    // 业务字段：cover / avatar
    @TableField("business_field")
    private String businessField;

    // 上传用户ID
    @TableField("upload_user_id")
    private Long uploadUserId;

    // 是否临时文件：1 是，0 否
    @TableField("is_temp")
    private Integer isTemp;

    // 状态：1 有效，0 失效（临时文件）
    @TableField("status")
    private Integer status;

    // 创建时间
    @TableField("create_time")
    private LocalDateTime createTime;

    // 过期时间（临时文件）
    @TableField("expire_time")
    private LocalDateTime expireTime;
}
