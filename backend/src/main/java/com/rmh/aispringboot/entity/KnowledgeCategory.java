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
 * 心理知识分类
 */
@Data
@TableName("knowledge_category")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class KnowledgeCategory {
    // 分类ID
    @TableId(type = IdType.AUTO)
    private Long id;

    // 父分类ID，0 表示顶级分类
    @TableField("parent_id")
    private Long parentId;

    // 分类名称
    @TableField("category_name")
    private String categoryName;

    // 分类编码
    @TableField("category_code")
    private String categoryCode;

    // 分类描述
    @TableField("description")
    private String description;

    // 排序号，越小越前
    @TableField("sort_order")
    private Integer sortOrder;

    // 状态：1 启用，0 停用
    @TableField("status")
    private Integer status;

    // 创建时间
    @TableField("created_at")
    private LocalDateTime createdAt;

    // 更新时间
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
