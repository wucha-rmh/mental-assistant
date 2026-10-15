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
 * 心理知识文章
 *
 * <p>主键是 36 位 UUID 字符串（不是自增），插入时由业务侧生成。
 */
@Data
@TableName("knowledge_article")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class KnowledgeArticle {
    // 文章ID（UUID）
    @TableId(type = IdType.INPUT)
    private String id;

    // 所属分类ID
    @TableField("category_id")
    private Long categoryId;

    // 标题
    @TableField("title")
    private String title;

    // 摘要
    @TableField("summary")
    private String summary;

    // 正文
    @TableField("content")
    private String content;

    // 封面图地址
    @TableField("cover_image")
    private String coverImage;

    // 标签，英文逗号分隔
    @TableField("tags")
    private String tags;

    // 作者用户ID
    @TableField("author_id")
    private Long authorId;

    // 阅读量
    @TableField("read_count")
    private Integer readCount;

    // 状态：1 已发布，0 草稿/下架
    @TableField("status")
    private Integer status;

    // 发布时间
    @TableField("published_at")
    private LocalDateTime publishedAt;

    // 创建时间
    @TableField("created_at")
    private LocalDateTime createdAt;

    // 更新时间
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
