package com.rmh.aispringboot.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rmh.aispringboot.DTO.command.UserFeedbackCreateCommandDTO;
import com.rmh.aispringboot.DTO.command.UserFeedbackReplyCommandDTO;
import com.rmh.aispringboot.DTO.response.UserFeedbackDTO;
import com.rmh.aispringboot.common.Result;
import com.rmh.aispringboot.service.FeedbackService;
import com.rmh.aispringboot.util.SecurityUserUtil;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * 用户留言（联系管理员）
 *
 * <p>用户端：提交留言、看自己的留言与管理员回复；
 * 管理端：列表（按状态筛选）、回复、删除、待处理条数角标。
 *
 * <p>数据范围由服务端决定：用户端只会拿到自己的留言，管理端的每条接口都先
 * {@code SecurityUserUtil.requireAdmin()}。
 */
@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    @Resource
    private FeedbackService feedbackService;

    /**
     * 提交留言（登录用户）
     */
    @PostMapping
    public Result<UserFeedbackDTO> create(@Valid @RequestBody UserFeedbackCreateCommandDTO command) {
        return Result.ok(feedbackService.create(command));
    }

    /**
     * 未登录留言（忘记密码、账号被禁用时的唯一通道）
     *
     * <p>白名单接口，见 {@code SecurityConfig.PUBLIC_PATH}。没有登录用户，
     * 所以留言不关联 {@code user_id}，而是把用户自己填的用户名写进 {@code guest_name}，
     * 管理员在后台能看到是谁留的、以及他填的联系方式。
     */
    @PostMapping("/guest")
    public Result<UserFeedbackDTO> createGuest(@Valid @RequestBody UserFeedbackCreateCommandDTO command) {
        return Result.ok(feedbackService.createAsGuest(command));
    }

    /**
     * 我的留言（登录用户，只返回自己的；分页参数兼容 pageNum/pageSize 与 currentPage/size）
     */
    @GetMapping("/mine")
    public Result<Page<UserFeedbackDTO>> pageMine(
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) Integer currentPage,
            @RequestParam(required = false) Integer size) {

        int page = pageNum != null ? pageNum : (currentPage != null ? currentPage : 1);
        int limit = pageSize != null ? pageSize : (size != null ? size : 10);

        return Result.ok(feedbackService.pageMine(page, limit));
    }

    /**
     * 留言列表（管理员）
     *
     * @param status       0 待处理 / 1 已回复，null 表示全部
     * @param feedbackType 留言类型 1-4，null 表示全部
     */
    @GetMapping("/admin/page")
    public Result<Page<UserFeedbackDTO>> pageForAdmin(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer feedbackType,
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) Integer currentPage,
            @RequestParam(required = false) Integer size) {

        SecurityUserUtil.requireAdmin();

        int page = pageNum != null ? pageNum : (currentPage != null ? currentPage : 1);
        int limit = pageSize != null ? pageSize : (size != null ? size : 10);

        return Result.ok(feedbackService.pageForAdmin(page, limit, status, feedbackType));
    }

    /**
     * 待处理留言条数（管理员，后台菜单角标）
     */
    @GetMapping("/admin/pending-count")
    public Result<Long> pendingCount() {
        SecurityUserUtil.requireAdmin();
        return Result.ok(feedbackService.countPending());
    }

    /**
     * 回复留言（管理员）：回复后状态自动变成「已回复」，用户端可见
     */
    @PutMapping("/admin/{id}/reply")
    public Result<UserFeedbackDTO> reply(
            @PathVariable("id") Long id,
            @Valid @RequestBody UserFeedbackReplyCommandDTO commandDTO) {

        SecurityUserUtil.requireAdmin();
        return Result.ok(feedbackService.reply(id, commandDTO.getReply()));
    }

    /**
     * 删除留言（管理员）
     */
    @DeleteMapping("/admin/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        SecurityUserUtil.requireAdmin();
        feedbackService.delete(id);
        return Result.ok();
    }
}
