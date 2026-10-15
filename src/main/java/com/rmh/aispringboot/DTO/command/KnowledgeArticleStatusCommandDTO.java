package com.rmh.aispringboot.DTO.command;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 文章上下架请求体：{@code {"status": 1}} 发布、{@code {"status": 2}} 下线
 *
 * <p>与 {@code knowledge_article.status} 的语义一致：1 已发布、2 已下线、0 草稿。
 */
@Data
public class KnowledgeArticleStatusCommandDTO {

    @NotNull(message = "状态不能为空")
    @Min(value = 0, message = "状态取值只能是 0（草稿）/ 1（已发布）/ 2（已下线）")
    @Max(value = 2, message = "状态取值只能是 0（草稿）/ 1（已发布）/ 2（已下线）")
    private Integer status;
}
