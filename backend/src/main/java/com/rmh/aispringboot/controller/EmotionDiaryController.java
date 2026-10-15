package com.rmh.aispringboot.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rmh.aispringboot.DTO.command.EmotionDiaryCreateCommandDTO;
import com.rmh.aispringboot.DTO.response.EmotionDiaryAdminItemDTO;
import com.rmh.aispringboot.common.Result;
import com.rmh.aispringboot.service.EmotionDiaryService;
import com.rmh.aispringboot.util.SecurityUserUtil;
import com.rmh.aispringboot.util.WebParamUtil;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 情绪日记
 *
 * <p>用户端可以记录（同一天重复提交按覆盖处理），管理后台可以分页查看与删除。
 */
@RestController
@RequestMapping("/api/emotion-diary")
public class EmotionDiaryController {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 10;

    @Autowired
    private EmotionDiaryService emotionDiaryService;

    /**
     * 管理后台情绪日记分页（仅管理员）
     *
     * <p>分页参数以前端实际发送的 {@code current/size} 为主，同时兼容
     * {@code pageNum/currentPage} 与 {@code pageSize}；筛选参数 {@code userId}、
     * {@code moodScreRange}（形如 1-3）为空串时表示不过滤。
     */
    @GetMapping("/admin/page")
    public Result<Page<EmotionDiaryAdminItemDTO>> adminPage(
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer currentPage,
            @RequestParam(required = false) Integer current,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String moodScreRange) {

        SecurityUserUtil.requireAdmin();
        int page = WebParamUtil.page(pageNum, currentPage, current, DEFAULT_PAGE);
        int pageSizeValue = WebParamUtil.size(pageSize, size, DEFAULT_SIZE);
        return Result.ok(emotionDiaryService.adminPage(page, pageSizeValue, userId, moodScreRange));
    }

    /**
     * 我的情绪日记分页（当前登录用户）
     *
     * <p>与后台列表共用同一套查询：{@code userId} 强制取登录态，前端无法指定，
     * 因此不可能看到别人的日记；可选 {@code diaryDate} 用来精确取某一天（编辑时判断新增还是更新）。
     */
    @GetMapping("/page")
    public Result<Page<EmotionDiaryAdminItemDTO>> myPage(
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer currentPage,
            @RequestParam(required = false) Integer current,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String moodScreRange,
            @RequestParam(required = false) String diaryDate) {

        Long userId = SecurityUserUtil.currentUser().getId();
        int page = WebParamUtil.page(pageNum, currentPage, current, DEFAULT_PAGE);
        int pageSizeValue = WebParamUtil.size(pageSize, size, DEFAULT_SIZE);
        return Result.ok(emotionDiaryService.userPage(page, pageSizeValue, userId, moodScreRange, diaryDate));
    }

    /**
     * 记录心情日记（登录用户）
     *
     * <p>{@code (user_id, diary_date)} 上有唯一键，同一天重复提交按覆盖更新处理，
     * 不会产生重复记录；已有 AI 分析结果保持不动。
     */
    @PostMapping
    public Result<EmotionDiaryAdminItemDTO> saveDiary(@Valid @RequestBody EmotionDiaryCreateCommandDTO command) {
        Long userId = SecurityUserUtil.currentUser().getId();
        return Result.ok(emotionDiaryService.saveDiary(userId, command));
    }

    /**
     * 删除自己的日记（登录用户）
     *
     * <p>服务层按「日记主键 + 登录用户」双条件校验，删不到别人的记录；
     * 与后台的 {@code DELETE /admin/{id}} 是两条独立路径，权限互不影响。
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteMyDiary(@PathVariable("id") Long id) {
        Long userId = SecurityUserUtil.currentUser().getId();
        emotionDiaryService.deleteOwnDiary(id, userId);
        return Result.ok();
    }

    /**
     * 删除一条情绪日记（仅管理员）
     *
     * <p>路径 id 是日记主键（Long），不是用户ID——前端列表把这一列的表头误标成了「用户ID」。
     */
    @DeleteMapping("/admin/{id}")
    public Result<Void> deleteDiary(@PathVariable("id") Long id) {
        SecurityUserUtil.requireAdmin();
        emotionDiaryService.deleteDiary(id);
        return Result.ok();
    }
}
