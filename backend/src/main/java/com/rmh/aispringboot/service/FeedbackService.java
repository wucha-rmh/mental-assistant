package com.rmh.aispringboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rmh.aispringboot.DTO.command.UserFeedbackCreateCommandDTO;
import com.rmh.aispringboot.DTO.response.UserFeedbackDTO;
import com.rmh.aispringboot.entity.User;
import com.rmh.aispringboot.entity.UserFeedback;
import com.rmh.aispringboot.exception.BusinessException;
import com.rmh.aispringboot.mapper.UserFeedbackMapper;
import com.rmh.aispringboot.mapper.UserMapper;
import com.rmh.aispringboot.util.SecurityUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 用户留言（联系管理员）
 *
 * <p>业务背景：用户忘记密码时没人能帮他重置 —— 站内没有邮箱/短信验证码，
 * 于是提供一条「给管理员留言」的通道：用户留一条消息（类型选「忘记密码/无法登录」），
 * 管理员在后台看到、回复，并把密码重置成用户指定的新密码（见
 * {@code UserService#resetPassword}）。
 *
 * <p>权限：留言与「我的留言」只看当前登录用户自己的数据（服务端按 userId 过滤，
 * 不信任前端传参）；列表、回复、删除都走 {@code SecurityUserUtil.requireAdmin()}。
 */
@Service
@RequiredArgsConstructor
public class FeedbackService {

    /** 待处理 */
    public static final int STATUS_PENDING = 0;

    /** 已回复 */
    public static final int STATUS_REPLIED = 1;

    private final UserFeedbackMapper userFeedbackMapper;
    private final UserMapper userMapper;

    /**
     * 提交留言（当前登录用户）
     */
    public UserFeedbackDTO create(UserFeedbackCreateCommandDTO command) {
        Long userId = SecurityUserUtil.currentUser().getId();
        LocalDateTime now = LocalDateTime.now();

        UserFeedback feedback = UserFeedback.builder()
                .userId(userId)
                .feedbackType(command.getFeedbackType())
                .content(command.getContent().trim())
                .contact(trimToNull(command.getContact()))
                .status(STATUS_PENDING)
                .createdAt(now)
                .updatedAt(now)
                .build();
        userFeedbackMapper.insert(feedback);

        return convertOne(feedback);
    }

    /**
     * 未登录留言（忘记密码、账号被禁用时的唯一通道）
     *
     * <p>没有登录用户，所以 {@code user_id} 留空，把用户自己填的用户名记进 {@code guest_name}；
     * 管理员在后台看到的就是这一列。除此之外与登录留言完全一样。
     *
     * <p>⚠️ 这是白名单接口（{@code SecurityConfig.PUBLIC_PATH}），任何人都能往表里写 ——
     * 单条 1000 字上限只能防爆库，防刷要靠网关层限流（`deploy/nginx.conf.example` 的 limit_req）。
     */
    public UserFeedbackDTO createAsGuest(UserFeedbackCreateCommandDTO command) {
        LocalDateTime now = LocalDateTime.now();

        UserFeedback feedback = UserFeedback.builder()
                .userId(null)
                .guestName(trimToNull(command.getGuestUsername()))
                .feedbackType(command.getFeedbackType())
                .content(command.getContent().trim())
                .contact(trimToNull(command.getContact()))
                .status(STATUS_PENDING)
                .createdAt(now)
                .updatedAt(now)
                .build();
        userFeedbackMapper.insert(feedback);

        return convertOne(feedback);
    }

    /**
     * 我的留言（只返回自己的，按时间倒序）
     */
    public Page<UserFeedbackDTO> pageMine(int page, int size) {
        Long userId = SecurityUserUtil.currentUser().getId();

        Page<UserFeedback> entityPage = userFeedbackMapper.selectPage(new Page<>(page, size),
                Wrappers.<UserFeedback>lambdaQuery()
                        .eq(UserFeedback::getUserId, userId)
                        .orderByDesc(UserFeedback::getId));
        return convert(entityPage);
    }

    /**
     * 留言列表（管理员）
     *
     * <p>排序：**待处理在前**，其次按 id 倒序（最新的留言在最上面）。
     *
     * @param status       0 待处理 / 1 已回复，null 表示不过滤
     * @param feedbackType 留言类型 1-4，null 表示不过滤
     */
    public Page<UserFeedbackDTO> pageForAdmin(int page, int size, Integer status, Integer feedbackType) {
        LambdaQueryWrapper<UserFeedback> wrapper = Wrappers.lambdaQuery(UserFeedback.class);
        if (status != null) {
            wrapper.eq(UserFeedback::getStatus, status);
        }
        if (feedbackType != null) {
            wrapper.eq(UserFeedback::getFeedbackType, feedbackType);
        }
        wrapper.orderByAsc(UserFeedback::getStatus)
                .orderByDesc(UserFeedback::getId);

        return convert(userFeedbackMapper.selectPage(new Page<>(page, size), wrapper));
    }

    /**
     * 待处理留言条数（管理员，用于后台菜单角标）
     */
    public Long countPending() {
        return userFeedbackMapper.selectCount(
                Wrappers.<UserFeedback>lambdaQuery().eq(UserFeedback::getStatus, STATUS_PENDING));
    }

    /**
     * 回复留言（管理员）：写入回复内容并把状态置为「已回复」
     */
    public UserFeedbackDTO reply(Long id, String replyText) {
        UserFeedback feedback = userFeedbackMapper.selectById(id);
        if (feedback == null) {
            throw new BusinessException("留言不存在或已删除");
        }

        LocalDateTime now = LocalDateTime.now();
        userFeedbackMapper.update(null, Wrappers.<UserFeedback>lambdaUpdate()
                .eq(UserFeedback::getId, id)
                .set(UserFeedback::getAdminReply, replyText.trim())
                .set(UserFeedback::getStatus, STATUS_REPLIED)
                .set(UserFeedback::getReplyAdminId, SecurityUserUtil.currentUser().getId())
                .set(UserFeedback::getRepliedAt, now)
                .set(UserFeedback::getUpdatedAt, now));

        return convertOne(userFeedbackMapper.selectById(id));
    }

    /**
     * 删除留言（管理员）
     */
    public void delete(Long id) {
        if (userFeedbackMapper.selectById(id) == null) {
            throw new BusinessException("留言不存在或已删除");
        }
        userFeedbackMapper.deleteById(id);
    }

    // ── 内部方法 ────────────────────────────────────────────────

    private UserFeedbackDTO convertOne(UserFeedback feedback) {
        return toDTO(feedback, loadUsers(Collections.singletonList(feedback.getUserId())));
    }

    private Page<UserFeedbackDTO> convert(Page<UserFeedback> entityPage) {
        Map<Long, User> users = loadUsers(entityPage.getRecords().stream()
                .map(UserFeedback::getUserId)
                .collect(Collectors.toList()));

        Page<UserFeedbackDTO> result =
                new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        result.setRecords(entityPage.getRecords().stream()
                .map(item -> toDTO(item, users))
                .collect(Collectors.toList()));
        return result;
    }

    /**
     * 一次性把这一页留言涉及的用户查出来（避免逐条查库）
     */
    private Map<Long, User> loadUsers(Collection<Long> ids) {
        Set<Long> distinct = ids.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (distinct.isEmpty()) {
            return Collections.emptyMap();
        }
        return userMapper.selectBatchIds(distinct).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (existing, ignored) -> existing));
    }

    private UserFeedbackDTO toDTO(UserFeedback feedback, Map<Long, User> users) {
        User user = (users == null || feedback.getUserId() == null) ? null : users.get(feedback.getUserId());
        return UserFeedbackDTO.builder()
                .id(feedback.getId())
                .userId(feedback.getUserId())
                .username(user == null ? null : user.getUsername())
                .nickname(user == null ? null : user.getNickname())
                .guestName(feedback.getGuestName())
                .feedbackType(feedback.getFeedbackType())
                .feedbackTypeName(typeName(feedback.getFeedbackType()))
                .content(feedback.getContent())
                .contact(feedback.getContact())
                .status(feedback.getStatus())
                .statusName(statusName(feedback.getStatus()))
                .adminReply(feedback.getAdminReply())
                .repliedAt(feedback.getRepliedAt())
                .createdAt(feedback.getCreatedAt())
                .build();
    }

    private static String typeName(Integer type) {
        if (type == null) {
            return "其它";
        }
        return switch (type) {
            case 1 -> "忘记密码/无法登录";
            case 2 -> "账号问题";
            case 3 -> "功能建议";
            default -> "其它";
        };
    }

    private static String statusName(Integer status) {
        if (status == null) {
            return "待处理";
        }
        return status == STATUS_REPLIED ? "已回复" : "待处理";
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
