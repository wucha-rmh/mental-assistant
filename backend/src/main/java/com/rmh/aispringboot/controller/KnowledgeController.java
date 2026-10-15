package com.rmh.aispringboot.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rmh.aispringboot.DTO.command.KnowledgeArticleCommandDTO;
import com.rmh.aispringboot.DTO.command.KnowledgeArticleStatusCommandDTO;
import com.rmh.aispringboot.DTO.response.KnowledgeArticleDTO;
import com.rmh.aispringboot.DTO.response.KnowledgeCategoryDTO;
import com.rmh.aispringboot.common.Result;
import com.rmh.aispringboot.service.KnowledgeService;
import com.rmh.aispringboot.util.SecurityUserUtil;
import com.rmh.aispringboot.util.WebParamUtil;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 心理知识：分类与文章的查询、以及文章的写操作
 *
 * <p>读接口对所有人开放（普通用户只能看到已发布文章）；新增、编辑、上下架、删除
 * 四个写接口仅管理员可用。
 */
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 10;

    @Autowired
    private KnowledgeService knowledgeService;

    /**
     * 分类列表
     *
     * <p>前端不递归 children，即使数据是树形也返回扁平数组。
     */
    @GetMapping("/category/tree")
    public Result<List<KnowledgeCategoryDTO>> categoryTree() {
        return Result.ok(knowledgeService.listCategoryTree());
    }

    /**
     * 文章分页
     *
     * <p>分页参数兼容 {@code pageNum/currentPage/current} 与 {@code pageSize/size}；
     * {@code title}、{@code categoryId}、{@code status} 为空串时表示不过滤。
     * 管理员可以看到草稿与已下线文章，普通用户只能看到已发布文章。
     */
    @GetMapping("/article/page")
    public Result<Page<KnowledgeArticleDTO>> articlePage(
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer currentPage,
            @RequestParam(required = false) Integer current,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortDirection) {

        boolean admin = SecurityUserUtil.isAdmin(SecurityUserUtil.currentUser());
        int page = WebParamUtil.page(pageNum, currentPage, current, DEFAULT_PAGE);
        int pageSizeValue = WebParamUtil.size(pageSize, size, DEFAULT_SIZE);
        return Result.ok(knowledgeService.pageArticles(page, pageSizeValue,
                title, categoryId, status, sortField, sortDirection, admin));
    }

    /**
     * 文章详情（路径 id 是 UUID 字符串，不是数字）
     *
     * <p>这个接口被前台阅读页和后台编辑弹窗共用：只有非管理员打开时才算一次阅读，
     * 否则管理员预览/编辑会把阅读量刷上去。
     */
    @GetMapping("/article/{id}")
    public Result<KnowledgeArticleDTO> articleDetail(@PathVariable("id") String id) {
        boolean reader = !SecurityUserUtil.isAdmin(SecurityUserUtil.currentUser());
        return Result.ok(reader
                ? knowledgeService.getArticleDetailAndCountView(id)
                : knowledgeService.getArticleDetail(id));
    }

    /**
     * 新增文章（仅管理员）
     *
     * <p>前端自带 id（有封面时是上传文件用的 UUID），沿用它可以保证文章与封面文件对得上；
     * 没带就由后端生成。新增一律按「已发布」入库。
     */
    @PostMapping("/article")
    public Result<KnowledgeArticleDTO> createArticle(@Valid @RequestBody KnowledgeArticleCommandDTO command) {
        Long authorId = SecurityUserUtil.requireAdmin().getId();
        return Result.ok(knowledgeService.createArticle(command, authorId));
    }

    /**
     * 编辑文章（仅管理员）
     *
     * <p>前端会把详情旧值（阅读量、作者、创建时间等）一起回传，Service 只更新白名单字段。
     */
    @PutMapping("/article/{id}")
    public Result<KnowledgeArticleDTO> updateArticle(@PathVariable("id") String id,
                                                     @Valid @RequestBody KnowledgeArticleCommandDTO command) {
        SecurityUserUtil.requireAdmin();
        return Result.ok(knowledgeService.updateArticle(id, command));
    }

    /**
     * 上下架（仅管理员）：1 发布、2 下线、0 草稿
     */
    @PutMapping("/article/{id}/status")
    public Result<KnowledgeArticleDTO> changeArticleStatus(@PathVariable("id") String id,
                                                           @Valid @RequestBody KnowledgeArticleStatusCommandDTO command) {
        SecurityUserUtil.requireAdmin();
        return Result.ok(knowledgeService.changeArticleStatus(id, command.getStatus()));
    }

    /**
     * 删除文章（仅管理员），顺带删除本地封面文件
     */
    @DeleteMapping("/article/{id}")
    public Result<Void> deleteArticle(@PathVariable("id") String id) {
        SecurityUserUtil.requireAdmin();
        knowledgeService.deleteArticle(id);
        return Result.ok();
    }
}
