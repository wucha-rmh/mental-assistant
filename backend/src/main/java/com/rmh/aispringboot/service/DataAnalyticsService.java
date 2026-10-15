package com.rmh.aispringboot.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.rmh.aispringboot.DTO.response.DataAnalyticsOverviewDTO;
import com.rmh.aispringboot.entity.ConsultationMessage;
import com.rmh.aispringboot.entity.ConsultationSession;
import com.rmh.aispringboot.entity.EmotionDiary;
import com.rmh.aispringboot.entity.User;
import com.rmh.aispringboot.mapper.ConsultationMessageMapper;
import com.rmh.aispringboot.mapper.ConsultationSessionMapper;
import com.rmh.aispringboot.mapper.EmotionDiaryMapper;
import com.rmh.aispringboot.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 数据看板统计服务
 *
 * <p>数据量很小（用户 / 日记 / 会话都只有几十条），因此直接全量取出后在内存里聚合，
 * 不再为了几条数据写一堆 group by SQL。
 *
 * <p>趋势窗口：往前取 {@link #TREND_DAYS} 天。咨询趋势与用户活跃度以「今天」为锚点（近期有会话时
 * 就是标准的最近 14 天）；情绪趋势单独以「最后一条日记的日期」为锚点——库里的日记数据远早于当前
 * 日期，若同样以今天为锚点，窗口内一条日记都没有、图表会全为 0（横轴仍是真实日期，不伪造数据）。
 */
@Service
@RequiredArgsConstructor
public class DataAnalyticsService {

    /** 趋势图天数 */
    private static final int TREND_DAYS = 14;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final UserMapper userMapper;
    private final EmotionDiaryMapper emotionDiaryMapper;
    private final ConsultationSessionMapper consultationSessionMapper;
    private final ConsultationMessageMapper consultationMessageMapper;

    /**
     * 看板总览
     */
    public DataAnalyticsOverviewDTO overview() {
        List<User> users = userMapper.selectList(Wrappers.lambdaQuery(User.class));
        List<EmotionDiary> diaries = emotionDiaryMapper.selectList(Wrappers.lambdaQuery(EmotionDiary.class));
        List<ConsultationSession> sessions = consultationSessionMapper.selectList(Wrappers.lambdaQuery(ConsultationSession.class));
        List<ConsultationMessage> messages = consultationMessageMapper.selectList(Wrappers.lambdaQuery(ConsultationMessage.class));

        LocalDate today = LocalDate.now();
        LocalDate lastDiaryDate = latestDiaryDate(diaries);

        // 咨询趋势 / 用户活跃度：锚点 = 今天与最后一条会话日期中较晚者
        List<LocalDate> days = buildTrendDays(laterOf(today, latestSessionDate(sessions)));
        // 情绪趋势：单独以最后一条日记日期为锚点（库里日记远早于今天，用今天做锚点会一条都取不到）
        List<LocalDate> diaryDays = buildTrendDays(lastDiaryDate == null ? today : lastDiaryDate);

        return DataAnalyticsOverviewDTO.builder()
                .systemOverview(buildSystemOverview(users, diaries, sessions, today))
                .consultationStats(buildConsultationStats(sessions, messages, days))
                .emotionTrend(buildEmotionTrend(diaries, diaryDays))
                .userActivity(buildUserActivity(users, diaries, sessions, days))
                .build();
    }

    // ── 系统概览 ────────────────────────────────────────────────────

    private DataAnalyticsOverviewDTO.SystemOverview buildSystemOverview(List<User> users,
                                                                       List<EmotionDiary> diaries,
                                                                       List<ConsultationSession> sessions,
                                                                       LocalDate today) {
        // 活跃用户 = 有日记记录或有咨询会话记录的用户（去重）
        Set<Long> activeUserIds = new HashSet<>();
        diaries.stream().map(EmotionDiary::getUserId).filter(Objects::nonNull).forEach(activeUserIds::add);
        sessions.stream().map(ConsultationSession::getUserId).filter(Objects::nonNull).forEach(activeUserIds::add);

        long todayNewDiaries = diaries.stream()
                .filter(diary -> today.equals(diary.getDiaryDate()))
                .count();
        long todayNewSessions = sessions.stream()
                .filter(session -> session.getStartedAt() != null && today.equals(session.getStartedAt().toLocalDate()))
                .count();
        double avgMoodScore = round1(diaries.stream()
                .filter(diary -> diary.getMoodScore() != null)
                .mapToInt(EmotionDiary::getMoodScore)
                .average()
                .orElse(0d));

        return DataAnalyticsOverviewDTO.SystemOverview.builder()
                .totalUsers(users.size())
                .activeUsers(activeUserIds.size())
                .totalDiaries(diaries.size())
                .todayNewDiaries(todayNewDiaries)
                .totalSessions(sessions.size())
                .todayNewSessions(todayNewSessions)
                .avgMoodScore(avgMoodScore)
                .build();
    }

    // ── 咨询统计 ────────────────────────────────────────────────────

    private DataAnalyticsOverviewDTO.ConsultationStats buildConsultationStats(List<ConsultationSession> sessions,
                                                                             List<ConsultationMessage> messages,
                                                                             List<LocalDate> days) {
        // 会话时长 = 该会话最后一条消息时间 - 开始时间；没有消息的会话不计入平均
        Map<Long, LocalDateTime> lastMessageTime = new HashMap<>();
        for (ConsultationMessage message : messages) {
            if (message.getSessionId() == null || message.getCreatedAt() == null) {
                continue;
            }
            lastMessageTime.merge(message.getSessionId(), message.getCreatedAt(),
                    (a, b) -> a.isAfter(b) ? a : b);
        }
        double avgDurationMinutes = round1(sessions.stream()
                .filter(session -> session.getId() != null
                        && session.getStartedAt() != null
                        && lastMessageTime.containsKey(session.getId()))
                .mapToDouble(session -> Math.max(0,
                        Duration.between(session.getStartedAt(), lastMessageTime.get(session.getId())).toMinutes()))
                .average()
                .orElse(0d));

        List<DataAnalyticsOverviewDTO.DailyTrendItem> dailyTrend = days.stream()
                .map(day -> {
                    List<ConsultationSession> daySessions = sessions.stream()
                            .filter(session -> session.getStartedAt() != null
                                    && day.equals(session.getStartedAt().toLocalDate()))
                            .collect(Collectors.toList());
                    long userCount = daySessions.stream()
                            .map(ConsultationSession::getUserId)
                            .filter(Objects::nonNull)
                            .distinct()
                            .count();
                    return DataAnalyticsOverviewDTO.DailyTrendItem.builder()
                            .date(day.format(DATE_FORMAT))
                            .sessionCount(daySessions.size())
                            .userCount(userCount)
                            .build();
                })
                .collect(Collectors.toList());

        return DataAnalyticsOverviewDTO.ConsultationStats.builder()
                .totalSessions(sessions.size())
                .avgDurationMinutes(avgDurationMinutes)
                .dailyTrend(dailyTrend)
                .build();
    }

    // ── 情绪趋势 ────────────────────────────────────────────────────

    private List<DataAnalyticsOverviewDTO.EmotionTrendItem> buildEmotionTrend(List<EmotionDiary> diaries,
                                                                             List<LocalDate> days) {
        return days.stream()
                .map(day -> {
                    List<EmotionDiary> dayDiaries = diaries.stream()
                            .filter(diary -> day.equals(diary.getDiaryDate()))
                            .collect(Collectors.toList());
                    double avgMoodScore = round1(dayDiaries.stream()
                            .filter(diary -> diary.getMoodScore() != null)
                            .mapToInt(EmotionDiary::getMoodScore)
                            .average()
                            .orElse(0d));
                    return DataAnalyticsOverviewDTO.EmotionTrendItem.builder()
                            .date(day.format(DATE_FORMAT))
                            .avgMoodScore(avgMoodScore)
                            .recordCount(dayDiaries.size())
                            .build();
                })
                .collect(Collectors.toList());
    }

    // ── 用户活跃度 ──────────────────────────────────────────────────

    private List<DataAnalyticsOverviewDTO.UserActivityItem> buildUserActivity(List<User> users,
                                                                             List<EmotionDiary> diaries,
                                                                             List<ConsultationSession> sessions,
                                                                             List<LocalDate> days) {
        return days.stream()
                .map(day -> {
                    Set<Long> diaryUsers = diaries.stream()
                            .filter(diary -> day.equals(diary.getDiaryDate()))
                            .map(EmotionDiary::getUserId)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toSet());
                    Set<Long> consultationUsers = sessions.stream()
                            .filter(session -> session.getStartedAt() != null
                                    && day.equals(session.getStartedAt().toLocalDate()))
                            .map(ConsultationSession::getUserId)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toSet());
                    long newUsers = users.stream()
                            .filter(user -> user.getCreatedAt() != null
                                    && day.equals(user.getCreatedAt().toLocalDate()))
                            .count();

                    Set<Long> activeUsers = new HashSet<>(diaryUsers);
                    activeUsers.addAll(consultationUsers);

                    return DataAnalyticsOverviewDTO.UserActivityItem.builder()
                            .date(day.format(DATE_FORMAT))
                            .activeUsers(activeUsers.size())
                            .newUsers(newUsers)
                            .diaryUsers(diaryUsers.size())
                            .consultationUsers(consultationUsers.size())
                            .build();
                })
                .collect(Collectors.toList());
    }

    // ── 内部工具 ────────────────────────────────────────────────────

    /**
     * 趋势窗口：以 anchor 为最后一天往前取 {@link #TREND_DAYS} 天（anchor 为 null 时用今天）。
     */
    private List<LocalDate> buildTrendDays(LocalDate anchor) {
        LocalDate end = anchor == null ? LocalDate.now() : anchor;
        List<LocalDate> days = new ArrayList<>(TREND_DAYS);
        for (int i = TREND_DAYS - 1; i >= 0; i--) {
            days.add(end.minusDays(i));
        }
        return days;
    }

    /**
     * 最后一条会话的日期（无会话时为 null）
     */
    private LocalDate latestSessionDate(List<ConsultationSession> sessions) {
        LocalDate latest = null;
        for (ConsultationSession session : sessions) {
            if (session.getStartedAt() == null) {
                continue;
            }
            LocalDate date = session.getStartedAt().toLocalDate();
            if (latest == null || date.isAfter(latest)) {
                latest = date;
            }
        }
        return latest;
    }

    /**
     * 最后一条日记的日期（无日记时为 null）
     */
    private LocalDate latestDiaryDate(List<EmotionDiary> diaries) {
        LocalDate latest = null;
        for (EmotionDiary diary : diaries) {
            if (diary.getDiaryDate() == null) {
                continue;
            }
            if (latest == null || diary.getDiaryDate().isAfter(latest)) {
                latest = diary.getDiaryDate();
            }
        }
        return latest;
    }

    /**
     * 取两个日期中较晚者（任一为 null 时返回另一个）
     */
    private LocalDate laterOf(LocalDate first, LocalDate second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return first.isAfter(second) ? first : second;
    }

    /**
     * 保留一位小数
     */
    private double round1(double value) {
        return Math.round(value * 10d) / 10d;
    }
}
