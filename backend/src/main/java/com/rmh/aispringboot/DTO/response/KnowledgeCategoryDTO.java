package com.rmh.aispringboot.DTO.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识分类（前端下拉与分类映射用）
 *
 * <p>注意：前端不递归 children，{@code /category/tree} 需要返回**扁平数组**。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class KnowledgeCategoryDTO {
    // 分类ID
    private Long id;
    // 父分类ID，0 表示顶级
    private Long parentId;
    // 分类名称
    private String categoryName;
}
