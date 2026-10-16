package com.rmh.aispringboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rmh.aispringboot.DTO.command.KnowledgeArticleCommandDTO;
import com.rmh.aispringboot.DTO.response.KnowledgeArticleDTO;
import com.rmh.aispringboot.DTO.response.KnowledgeCategoryDTO;
import com.rmh.aispringboot.entity.KnowledgeArticle;
import com.rmh.aispringboot.entity.KnowledgeCategory;
import com.rmh.aispringboot.entity.User;
import com.rmh.aispringboot.exception.BusinessException;
import com.rmh.aispringboot.mapper.KnowledgeArticleMapper;
import com.rmh.aispringboot.mapper.KnowledgeCategoryMapper;
import com.rmh.aispringboot.mapper.UserMapper;
import com.rmh.aispringboot.util.WebParamUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 心理知识（分类 + 文章）服务
 *
 * <p>读接口：分类列表、文章分页、文章详情（前台/后台共用）。
 * 写接口：新增、编辑、上下架、删除（仅管理员，见 {@code KnowledgeController}）。
 */
@Service
@RequiredArgsConstructor
public class KnowledgeService {

    /** 已发布状态（前端列表里 status=1 的行显示「下线」按钮） */
    private static final int STATUS_PUBLISHED = 1;

    /** 草稿状态：新增的文章一律先落草稿，需管理员在列表里点「发布」才对外可见 */
    private static final int STATUS_DRAFT = 0;

    private final KnowledgeCategoryMapper knowledgeCategoryMapper;
    private final KnowledgeArticleMapper knowledgeArticleMapper;
    private final UserMapper userMapper;
    private final FileStorageService fileStorageService;

    /**
     * 分类列表
     *
     * <p>前端不递归 children，因此即使数据是树形也返回**扁平数组**（按 sort_order 排序）。
     */
    public List<KnowledgeCategoryDTO> listCategoryTree() {
        List<KnowledgeCategory> categories = knowledgeCategoryMapper.selectList(
                Wrappers.<KnowledgeCategory>lambdaQuery()
                        .orderByAsc(KnowledgeCategory::getSortOrder)
                        .orderByAsc(KnowledgeCategory::getId));

        return categories.stream()
                .map(category -> KnowledgeCategoryDTO.builder()
                        .id(category.getId())
                        .parentId(category.getParentId())
                        .categoryName(category.getCategoryName())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 文章分页
     *
     * @param includeUnpublished 管理员为 true（可看草稿/已下线），普通用户只能看到已发布文章
     * @param title              标题模糊筛选，空串表示不过滤
     * @param categoryId         分类筛选，空串表示不过滤
     * @param status             状态筛选，空串表示不过滤
     * @param sortField          排序字段白名单：publishedAt（默认，后台未显式指定时按 updatedAt 倒序）/ readCount
     * @param sortDirection      asc / desc（默认 desc）
     */
    public Page<KnowledgeArticleDTO> pageArticles(int page, int size,
                                                  String title, String categoryId, String status,
                                                  String sortField, String sortDirection,
                                                  boolean includeUnpublished) {
        LambdaQueryWrapper<KnowledgeArticle> wrapper = Wrappers.lambdaQuery(KnowledgeArticle.class);

        if (StringUtils.hasText(title)) {
            wrapper.like(KnowledgeArticle::getTitle, title.trim());
        }
        Long categoryIdValue = WebParamUtil.parseLong(categoryId);
        if (categoryIdValue != null) {
            wrapper.eq(KnowledgeArticle::getCategoryId, categoryIdValue);
        }
        Integer statusValue = WebParamUtil.parseInt(status);
        if (statusValue != null) {
            wrapper.eq(KnowledgeArticle::getStatus, statusValue);
        } else if (!includeUnpublished) {
            // 前台只展示已发布文章
            wrapper.eq(KnowledgeArticle::getStatus, STATUS_PUBLISHED);
        }

        boolean asc = "asc".equalsIgnoreCase(sortDirection);
        if ("readCount".equalsIgnoreCase(sortField)) {
            wrapper.orderBy(true, asc, KnowledgeArticle::getReadCount);
        } else if (!StringUtils.hasText(sortField) && includeUnpublished) {
            // 后台列表默认按「最后修改时间」倒序：草稿的 published_at 为 null，
            // 若沿用发布时间排序会整列沉到最后一页，新建/编辑完看不到自己的文章
            wrapper.orderByDesc(KnowledgeArticle::getUpdatedAt)
                    .orderByDesc(KnowledgeArticle::getId);
        } else {
            wrapper.orderBy(true, asc, KnowledgeArticle::getPublishedAt)
                    .orderByDesc(KnowledgeArticle::getId);
        }

        Page<KnowledgeArticle> entityPage = knowledgeArticleMapper.selectPage(new Page<>(page, size), wrapper);

        Map<Long, String> categoryNames = loadCategoryNames(extractCategoryIds(entityPage.getRecords()));
        Map<Long, String> authorNames = loadAuthorNames(extractAuthorIds(entityPage.getRecords()));

        Page<KnowledgeArticleDTO> result =
                new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        result.setRecords(entityPage.getRecords().stream()
                .map(article -> toDTO(article, categoryNames, authorNames))
                .collect(Collectors.toList()));
        return result;
    }

    /**
     * 文章详情
     */
    public KnowledgeArticleDTO getArticleDetail(String id) {
        KnowledgeArticle article = knowledgeArticleMapper.selectById(id);
        if (article == null) {
            throw new BusinessException("文章不存在或已删除");
        }
        Map<Long, String> categoryNames = loadCategoryNames(Collections.singletonList(article.getCategoryId()));
        Map<Long, String> authorNames = loadAuthorNames(Collections.singletonList(article.getAuthorId()));
        return toDTO(article, categoryNames, authorNames);
    }

    /**
     * 前台阅读入口：读取详情并把阅读量 +1。
     *
     * <p>后台编辑弹窗与文章写操作都复用 {@link #getArticleDetail(String)}，不计数；
     * 自增走 SQL 表达式（不是「先查后写」），并发阅读不会互相覆盖。
     */
    public KnowledgeArticleDTO getArticleDetailAndCountView(String id) {
        KnowledgeArticleDTO dto = getArticleDetail(id);
        knowledgeArticleMapper.update(null, Wrappers.lambdaUpdate(KnowledgeArticle.class)
                .eq(KnowledgeArticle::getId, id)
                .setSql("read_count = IFNULL(read_count, 0) + 1"));
        dto.setReadCount((dto.getReadCount() == null ? 0 : dto.getReadCount()) + 1);
        return dto;
    }

    // ── 写操作（仅管理员） ──────────────────────────────────────────

    /**
     * 新增文章
     *
     * <p>前端上传封面时已经用 {@code crypto.randomUUID()} 生成过 id 并写进
     * {@code sys_file_info.business_id}，这里优先沿用，避免封面与文章对不上号。
     * 前端不提交 status：新增一律落「草稿」({@link #STATUS_DRAFT})、{@code published_at} 留空，
     * 管理员在列表里点「发布」（{@code PUT /api/knowledge/article/{id}/status}）时才盖上发布时间。
     *
     * @param authorId 当前登录管理员ID
     */
    public KnowledgeArticleDTO createArticle(KnowledgeArticleCommandDTO command, Long authorId) {
        String id = StringUtils.hasText(command.getId()) ? command.getId().trim() : UUID.randomUUID().toString();
        if (knowledgeArticleMapper.selectById(id) != null) {
            throw new BusinessException("文章已存在，请关闭弹窗后重试");
        }
        Long categoryId = parseCategoryId(command.getCategoryId());

        LocalDateTime now = LocalDateTime.now();
        KnowledgeArticle article = KnowledgeArticle.builder()
                .id(id)
                .categoryId(categoryId)
                .title(command.getTitle().trim())
                .summary(trimToNull(command.getSummary()))
                .content(command.getContent())
                .coverImage(trimToEmpty(command.getCoverImage()))
                .tags(trimToNull(command.getTags()))
                .authorId(authorId)
                .readCount(0)
                .status(STATUS_DRAFT)
                .publishedAt(null)
                .createdAt(now)
                .updatedAt(now)
                .build();
        knowledgeArticleMapper.insert(article);
        return getArticleDetail(id);
    }

    /**
     * 编辑文章
     *
     * <p>用 {@link LambdaUpdateWrapper} 显式 set 每个可编辑字段：MyBatis-Plus 的
     * {@code updateById} 默认忽略 null，会把「清空摘要 / 移除封面」这类操作吞掉。
     * 状态、阅读量、作者、创建时间不在编辑范围内，保持原值。
     */
    public KnowledgeArticleDTO updateArticle(String id, KnowledgeArticleCommandDTO command) {
        KnowledgeArticle article = knowledgeArticleMapper.selectById(id);
        if (article == null) {
            throw new BusinessException("文章不存在或已删除");
        }
        Long categoryId = parseCategoryId(command.getCategoryId());

        String oldCover = article.getCoverImage();
        String newCover = trimToEmpty(command.getCoverImage());

        LambdaUpdateWrapper<KnowledgeArticle> update = Wrappers.lambdaUpdate(KnowledgeArticle.class)
                .eq(KnowledgeArticle::getId, id)
                .set(KnowledgeArticle::getTitle, command.getTitle().trim())
                .set(KnowledgeArticle::getCategoryId, categoryId)
                .set(KnowledgeArticle::getSummary, trimToNull(command.getSummary()))
                .set(KnowledgeArticle::getContent, command.getContent())
                .set(KnowledgeArticle::getCoverImage, newCover)
                .set(KnowledgeArticle::getTags, trimToNull(command.getTags()))
                .set(KnowledgeArticle::getUpdatedAt, LocalDateTime.now());
        knowledgeArticleMapper.update(null, update);

        // 换过封面就把旧图删掉，避免 uploads 里堆孤儿文件
        if (oldCover != null && !oldCover.equals(newCover)) {
            fileStorageService.deleteStoredFile(oldCover);
        }
        return getArticleDetail(id);
    }

    /**
     * 上下架：1 发布、2 下线、0 草稿
     *
     * <p>发布时若还没有发布时间就补上；下线再上线不会覆盖首次发布时间。
     */
    public KnowledgeArticleDTO changeArticleStatus(String id, Integer status) {
        KnowledgeArticle article = knowledgeArticleMapper.selectById(id);
        if (article == null) {
            throw new BusinessException("文章不存在或已删除");
        }
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<KnowledgeArticle> update = Wrappers.lambdaUpdate(KnowledgeArticle.class)
                .eq(KnowledgeArticle::getId, id)
                .set(KnowledgeArticle::getStatus, status)
                .set(KnowledgeArticle::getUpdatedAt, now);
        if (status == STATUS_PUBLISHED && article.getPublishedAt() == null) {
            update.set(KnowledgeArticle::getPublishedAt, now);
        }
        knowledgeArticleMapper.update(null, update);
        return getArticleDetail(id);
    }

    /**
     * 删除文章，顺带清掉它的本地封面文件（只删 {@code /files/} 下的）
     */
    public void deleteArticle(String id) {
        KnowledgeArticle article = knowledgeArticleMapper.selectById(id);
        if (article == null) {
            throw new BusinessException("文章不存在或已删除");
        }
        knowledgeArticleMapper.deleteById(id);
        fileStorageService.deleteStoredFile(article.getCoverImage());
    }

    // ── 内部工具 ────────────────────────────────────────────────────

    /**
     * 解析并校验分类ID
     *
     * <p>前端 select 未选择时提交的是空串（且提交前的表单校验不会拦住请求），
     * 所以这里统一按字符串收、再解析成数字并确认分类真实存在，避免写入脏 categoryId。
     */
    private Long parseCategoryId(String raw) {
        Long categoryId = WebParamUtil.parseLong(raw);
        if (categoryId == null || knowledgeCategoryMapper.selectById(categoryId) == null) {
            throw new BusinessException("所选分类不存在");
        }
        return categoryId;
    }

    /** 空白统一成空串：封面会被前端拼成 {@code fileBaseUrl + coverImage}，存 null 会变成 "null" */
    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    /** 空白字符串统一成 null（前端会提交空串） */
    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private List<Long> extractCategoryIds(List<KnowledgeArticle> articles) {
        return articles.stream()
                .map(KnowledgeArticle::getCategoryId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    private List<Long> extractAuthorIds(List<KnowledgeArticle> articles) {
        return articles.stream()
                .map(KnowledgeArticle::getAuthorId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    private Map<Long, String> loadCategoryNames(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return knowledgeCategoryMapper.selectBatchIds(ids).stream()
                .filter(category -> category.getId() != null)
                .collect(Collectors.toMap(KnowledgeCategory::getId,
                        category -> category.getCategoryName() == null ? "" : category.getCategoryName(),
                        (a, b) -> a));
    }

    private Map<Long, String> loadAuthorNames(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return userMapper.selectBatchIds(ids).stream()
                .filter(user -> user.getId() != null)
                .collect(Collectors.toMap(User::getId, User::getDisplayName, (a, b) -> a));
    }

    private KnowledgeArticleDTO toDTO(KnowledgeArticle article,
                                      Map<Long, String> categoryNames,
                                      Map<Long, String> authorNames) {
        return KnowledgeArticleDTO.builder()
                .id(article.getId())
                .categoryId(article.getCategoryId())
                .categoryName(article.getCategoryId() == null ? null : categoryNames.get(article.getCategoryId()))
                .title(article.getTitle())
                .summary(article.getSummary())
                .content(article.getContent())
                .coverImage(article.getCoverImage())
                .tags(article.getTags())
                .tagArray(splitTags(article.getTags()))
                .authorId(article.getAuthorId())
                .authorName(article.getAuthorId() == null ? null : authorNames.get(article.getAuthorId()))
                .readCount(article.getReadCount() == null ? 0 : article.getReadCount())
                .status(article.getStatus())
                .publishedAt(article.getPublishedAt())
                .createdAt(article.getCreatedAt())
                .updatedAt(article.getUpdatedAt())
                .build();
    }

    /**
     * 标签字符串 → 数组，永不为 null（前端会直接 v-for）
     */
    private List<String> splitTags(String tags) {
        if (!StringUtils.hasText(tags)) {
            return new ArrayList<>();
        }
        return Arrays.stream(tags.split("[,，]"))
                .map(String::trim)
                .filter(tag -> !tag.isEmpty())
                .collect(Collectors.toList());
    }
}
