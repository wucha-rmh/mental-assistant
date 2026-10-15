package com.rmh.aispringboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rmh.aispringboot.DTO.command.EmotionDiaryCreateCommandDTO;
import com.rmh.aispringboot.DTO.response.EmotionDiaryAdminItemDTO;
import com.rmh.aispringboot.entity.EmotionDiary;
import com.rmh.aispringboot.entity.User;
import com.rmh.aispringboot.exception.BusinessException;
import com.rmh.aispringboot.mapper.EmotionDiaryMapper;
import com.rmh.aispringboot.mapper.UserMapper;
import com.rmh.aispringboot.util.WebParamUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 情绪日记服务
 *
 * <p>读接口：管理后台分页列表。
 * 写接口：用户端「记录今天的心情」（同一天重复提交按覆盖处理）、管理后台删除。
 */
@Service
@RequiredArgsConstructor
public class EmotionDiaryService {

    private final EmotionDiaryMapper emotionDiaryMapper;
    private final UserMapper userMapper;

    /**
     * 管理后台情绪日记分页
     *
     * @param userId         用户ID筛选，空串表示不过滤
     * @param moodScreRange  心情区间筛选，形如 {@code 1-3} / {@code 4-6} / {@code 7-10}
     *                       （前端参数名就是 {@code moodScreRange}，此处沿用以保持兼容）
     */
    public Page<EmotionDiaryAdminItemDTO> adminPage(int page, int size, String userId, String moodScreRange) {
        return query(page, size, WebParamUtil.parseLong(userId), moodScreRange, null);
    }

    /**
     * 用户端「我的记录」分页
     *
     * <p>{@code userId} 由登录态传入（不接受前端指定），因此不可能查到别人的日记；
     * {@code diaryDate} 可选，用来精确取某一天的记录（前端编辑某天时用它判断「已有/新增」）。
     */
    public Page<EmotionDiaryAdminItemDTO> userPage(int page, int size, Long userId, String moodScreRange, String diaryDate) {
        return query(page, size, userId, moodScreRange, parseFilterDate(diaryDate));
    }

    /**
     * 情绪日记分页查询（后台与用户端共用）
     *
     * @param userIdValue   用户ID，null 表示不过滤
     * @param moodScreRange 心情区间，形如 {@code 1-3}
     * @param diaryDate     具体日期，null 表示不过滤
     */
    private Page<EmotionDiaryAdminItemDTO> query(int page, int size, Long userIdValue,
                                                 String moodScreRange, LocalDate diaryDate) {
        LambdaQueryWrapper<EmotionDiary> wrapper = Wrappers.lambdaQuery(EmotionDiary.class);

        if (userIdValue != null) {
            wrapper.eq(EmotionDiary::getUserId, userIdValue);
        }
        int[] moodRange = parseMoodRange(moodScreRange);
        if (moodRange != null) {
            wrapper.between(EmotionDiary::getMoodScore, moodRange[0], moodRange[1]);
        }
        if (diaryDate != null) {
            wrapper.eq(EmotionDiary::getDiaryDate, diaryDate);
        }
        wrapper.orderByDesc(EmotionDiary::getDiaryDate).orderByDesc(EmotionDiary::getId);

        Page<EmotionDiary> entityPage = emotionDiaryMapper.selectPage(new Page<>(page, size), wrapper);

        List<Long> userIds = entityPage.getRecords().stream()
                .map(EmotionDiary::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, User> userMap = loadUsers(userIds);

        Page<EmotionDiaryAdminItemDTO> result =
                new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        result.setRecords(entityPage.getRecords().stream()
                .map(diary -> toDTO(diary, userMap))
                .collect(Collectors.toList()));
        return result;
    }

    // ── 写操作 ──────────────────────────────────────────────────────

    /**
     * 记录 / 更新某一天的心情日记（用户端）
     *
     * <p>{@code emotion_diary} 上 (user_id, diary_date) 有唯一键，前端又是
     * 「记录今天的心情」的单页表单，所以同一天重复提交按**覆盖**处理而不是报错。
     * {@code userId} 一律取自登录态，不接受前端传入（防写别人的日记）。
     *
     * <p>注意：原先预留的 {@code ai_emotion_analysis} / {@code ai_analysis_updated_at} 两列
     * 已于 2026-10-15 随「日记 AI 分析」一起删除，日记只剩心情评分 + 今日感想。
     */
    public EmotionDiaryAdminItemDTO saveDiary(Long userId, EmotionDiaryCreateCommandDTO command) {
        LocalDate diaryDate = parseDiaryDate(command.getDiaryDate());
        // 日记是记录「已经发生的心情」，未来日期没有意义（前端日期选择器也不让选，这里兜底）
        if (diaryDate.isAfter(LocalDate.now())) {
            throw new BusinessException("不能记录未来日期的日记");
        }
        EmotionDiary existing = emotionDiaryMapper.selectOne(Wrappers.<EmotionDiary>lambdaQuery()
                .eq(EmotionDiary::getUserId, userId)
                .eq(EmotionDiary::getDiaryDate, diaryDate)
                .last("limit 1"));

        LocalDateTime now = LocalDateTime.now();
        if (existing != null) {
            LambdaUpdateWrapper<EmotionDiary> update = Wrappers.lambdaUpdate(EmotionDiary.class)
                    .eq(EmotionDiary::getId, existing.getId())
                    .set(EmotionDiary::getMoodScore, command.getMoodScore())
                    .set(EmotionDiary::getDiaryContent, trimToNull(command.getDiaryContent()))
                    .set(EmotionDiary::getUpdatedAt, now);
            emotionDiaryMapper.update(null, update);
            return toDTO(emotionDiaryMapper.selectById(existing.getId()), loadUsers(List.of(userId)));
        }

        EmotionDiary diary = EmotionDiary.builder()
                .userId(userId)
                .diaryDate(diaryDate)
                .moodScore(command.getMoodScore())
                .diaryContent(trimToNull(command.getDiaryContent()))
                .createdAt(now)
                .updatedAt(now)
                .build();
        emotionDiaryMapper.insert(diary);
        return toDTO(diary, loadUsers(List.of(userId)));
    }

    /**
     * 删除一条情绪日记（管理后台）
     */
    public void deleteDiary(Long id) {
        EmotionDiary diary = emotionDiaryMapper.selectById(id);
        if (diary == null) {
            throw new BusinessException("日记不存在或已删除");
        }
        emotionDiaryMapper.deleteById(id);
    }

    /**
     * 删除自己的日记（用户端）
     *
     * <p>「不存在」与「不属于当前用户」返回同一句提示，避免用 id 探测别人有没有这条日记。
     */
    public void deleteOwnDiary(Long id, Long userId) {
        EmotionDiary diary = emotionDiaryMapper.selectById(id);
        if (diary == null || userId == null || !userId.equals(diary.getUserId())) {
            throw new BusinessException("日记不存在或无权访问");
        }
        emotionDiaryMapper.deleteById(id);
    }

    // ── 内部工具 ────────────────────────────────────────────────────

    /**
     * 解析「筛选用的日期」：空白返回 null（表示不按日期过滤），格式非法直接报错
     */
    private LocalDate parseFilterDate(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.length() > 10) {
            trimmed = trimmed.substring(0, 10);
        }
        try {
            return LocalDate.parse(trimmed);
        } catch (DateTimeParseException e) {
            throw new BusinessException("日期格式不正确，应为 yyyy-MM-dd");
        }
    }

    /**
     * 解析日记日期：空白按服务器当天；纯日期或带时间的字符串都取前 10 位
     */
    private LocalDate parseDiaryDate(String raw) {
        if (!StringUtils.hasText(raw)) {
            return LocalDate.now();
        }
        String trimmed = raw.trim();
        if (trimmed.length() > 10) {
            trimmed = trimmed.substring(0, 10);
        }
        try {
            return LocalDate.parse(trimmed);
        } catch (DateTimeParseException e) {
            throw new BusinessException("日期格式不正确，应为 yyyy-MM-dd");
        }
    }

    /** 空白字符串统一成 null（前端会提交空串） */
    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 解析心情区间，形如 1-3；解析不出来就返回 null（表示不过滤）
     */
    private int[] parseMoodRange(String moodScreRange) {
        if (!StringUtils.hasText(moodScreRange)) {
            return null;
        }
        String[] parts = moodScreRange.trim().split("-");
        if (parts.length != 2) {
            return null;
        }
        Integer min = WebParamUtil.parseInt(parts[0]);
        Integer max = WebParamUtil.parseInt(parts[1]);
        if (min == null || max == null) {
            return null;
        }
        return new int[]{Math.min(min, max), Math.max(min, max)};
    }

    private Map<Long, User> loadUsers(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return userMapper.selectBatchIds(ids).stream()
                .filter(user -> user.getId() != null)
                .collect(Collectors.toMap(User::getId, user -> user, (a, b) -> a));
    }

    private EmotionDiaryAdminItemDTO toDTO(EmotionDiary diary, Map<Long, User> userMap) {
        User user = diary.getUserId() == null ? null : userMap.get(diary.getUserId());
        return EmotionDiaryAdminItemDTO.builder()
                .id(diary.getId())
                .userId(diary.getUserId())
                .username(user == null ? null : user.getUsername())
                .nickname(user == null ? null : user.getNickname())
                .diaryDate(diary.getDiaryDate())
                .moodScore(diary.getMoodScore())
                .diaryContent(diary.getDiaryContent())
                .createdAt(diary.getCreatedAt())
                .updatedAt(diary.getUpdatedAt())
                .build();
    }
}
