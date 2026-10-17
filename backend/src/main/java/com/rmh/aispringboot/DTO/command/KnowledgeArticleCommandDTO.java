package com.rmh.aispringboot.DTO.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 心理知识文章的新增 / 编辑请求体
 *
 * <p>字段与前端 {@code ArticleDialog.vue} 提交的 {@code submitData} 一一对应：
 * 前端上传封面时会用 {@code crypto.randomUUID()} 生成 {@code id} 并作为
 * {@code sys_file_info.business_id}，所以新增时可以带上 {@code id}（不带则由后端生成）。
 *
 * <p>前端不提交 {@code status}：新增一律落「草稿」（{@code status=0}、{@code published_at} 为空），
 * 需要管理员在列表里点「发布」，上下架走 {@code PUT /api/knowledge/article/{id}/status}。
 */
@Data
public class KnowledgeArticleCommandDTO {

    /** 文章ID（36 位 UUID）。前端已生成时沿用，为空则后端生成 */
    @Size(max = 36, message = "文章ID长度不正确")
    private String id;

    @NotBlank(message = "文章标题不能为空")
    @Size(max = 200, message = "文章标题最多200个字符")
    private String title;

    /**
     * 所属分类ID。
     *
     * <p>前端 select 绑的是数字，但表单初始值是空串、且提交前的校验不会拦住请求
     * （{@code ArticleDialog.vue} 的 {@code if (valid)} 里没有 else return），
     * 所以这里按字符串收，由 Service 统一解析并校验分类是否存在。
     */
    @Size(max = 20, message = "分类ID长度不正确")
    private String categoryId;

    @Size(max = 2000, message = "文章摘要最多2000个字符")
    private String summary;

    @NotBlank(message = "文章内容不能为空")
    @Size(max = 20000, message = "文章内容最多20000个字符")
    private String content;

    /** 封面地址：{@code /files/...} 相对路径或空串（前端会拼 fileBaseUrl） */
    @Size(max = 500, message = "封面地址最多500个字符")
    private String coverImage;

    @Size(max = 500, message = "标签最多500个字符")
    private String tags;
}
