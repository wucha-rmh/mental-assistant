package com.rmh.aispringboot.DTO.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识文章（列表与详情共用）
 *
 * <p>前端约定：
 * <ul>
 *   <li>{@code categoryId} 必须是数字，否则后台列表按 {@code categoryMap[categoryId]} 取不到分类名</li>
 *   <li>{@code status} 必须是数字（1 已发布，0/2 未发布），前端用严格 {@code ===} 判断</li>
 *   <li>{@code tagArray} 必须是数组，详情页直接 {@code v-for}，null 会让页面报错</li>
 * </ul>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class KnowledgeArticleDTO {
    // 文章ID（UUID）
    private String id;
    // 分类ID
    private Long categoryId;
    // 分类名称
    private String categoryName;
    // 标题
    private String title;
    // 摘要
    private String summary;
    // 正文（前端按 HTML 渲染）
    private String content;
    // 封面图
    private String coverImage;
    // 标签原始字符串（逗号分隔）
    private String tags;
    // 标签数组（前端遍历用，永远不是 null）
    private List<String> tagArray;
    // 作者ID
    private Long authorId;
    // 作者显示名（昵称优先）
    private String authorName;
    // 阅读量
    private Integer readCount;
    // 状态：1 已发布，0 草稿，2 已下线
    private Integer status;
    // 发布时间
    private LocalDateTime publishedAt;
    // 创建时间
    private LocalDateTime createdAt;
    // 更新时间
    private LocalDateTime updatedAt;
}
