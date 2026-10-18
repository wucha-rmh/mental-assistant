package com.rmh.aispringboot.service;

import com.rmh.aispringboot.DTO.response.DataAnalyticsOverviewDTO;
import com.rmh.aispringboot.DTO.response.analytics.DailyActiveUserRow;
import com.rmh.aispringboot.DTO.response.analytics.DailyActivityRow;
import com.rmh.aispringboot.DTO.response.analytics.DailyEmotionRow;
import com.rmh.aispringboot.DTO.response.analytics.DailySessionRow;
import com.rmh.aispringboot.DTO.response.analytics.SystemSnapshotRow;
import com.rmh.aispringboot.mapper.DataAnalyticsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 数据看板统计服务
 *
 * <p>聚合下推到数据库（DataAnalyticsMapper）：只把「按天分组的少量行」带回 JVM，
 * 不再像以前那样把 user / emotion_diary / consultation_session / consultation_message
 * 四张表整表 selectList 进来再在内存里数。
 *
 * <p>窗口起止日期仍由本类计算后传给 SQL（锚点语义留在 Java，SQL 不取 CURDATE()）：
 * 咨询趋势与用户活跃度以「今天与最后一条会话日期中较晚者」为最后一天；
 * 情绪趋势单独以「最后一条日记的日期」为最后一天（库里日记远早于今天，用今天做锚点会一条都取不到）。
 *
 * <p>SQL 只返回「有数据的日期」，14 天窗口的补零在本类做；四舍五入也统一在本类的 round1。
 */
@Service
@RequiredArgsConstructor
public class DataAnalyticsService {

    /** 趋势图天数 */
    private static final int TREND_DAYS = 14;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final DataAnalyticsMapper analyticsMapper;

    /**
     * 看板总览
     */
    public DataAnalyticsOverviewDTO overview() {
        LocalDate today = LocalDate.now();

        // 1) 概览标量：一次往返拿 6 个数字
        SystemSnapshotRow snapshot = analyticsMapper.selectSystemSnapshot(
                today, today.atStartOfDay(), today.plusDays(1).atStartOfDay());
        if (snapshot == null) {
            snapshot = new SystemSnapshotRow();
        }
        Long activeUserCount = analyticsMapper.selectActiveUserCount();
        Double avgDurationMinutes = analyticsMapper.selectAvgDurationMinutes();

        // 2) 两个锚点：最后一条会话日期 / 最后一条日记日期（可能为 null）
        LocalDate lastSessionDate = toDate(analyticsMapper.selectLatestSessionAt());
        LocalDate lastDiaryDate = analyticsMapper.selectLatestDiaryDate();

        List<LocalDate> days = buildTrendDays(laterOf(today, lastSessionDate));
        List<LocalDate> diaryDays = buildTrendDays(lastDiaryDate == null ? today : lastDiaryDate);

        // 3) 窗口内的按天聚合结果，按日期建索引
        Map<LocalDate, DailySessionRow> sessionByDay = indexByDate(
                analyticsMapper.selectDailySessions(days.get(0), endExclusiveOf(days)),
                DailySessionRow::getStatDate);
        Map<LocalDate, DailyEmotionRow> emotionByDay = indexByDate(
                analyticsMapper.selectDailyEmotions(diaryDays.get(0), endExclusiveOf(diaryDays)),
                DailyEmotionRow::getStatDate);
        Map<LocalDate, DailyActivityRow> activityByDay = indexByDate(
                analyticsMapper.selectDailyActivities(days.get(0), endExclusiveOf(days)),
                DailyActivityRow::getStatDate);
        Map<LocalDate, DailyActiveUserRow> activeUserByDay = indexByDate(
                analyticsMapper.selectDailyActiveUsers(days.get(0), endExclusiveOf(days)),
                DailyActiveUserRow::getStatDate);

        // 4) 按 14 天窗口展开补零后组装
        return DataAnalyticsOverviewDTO.builder()
                .systemOverview(buildSystemOverview(snapshot, activeUserCount))
                .consultationStats(buildConsultationStats(snapshot, avgDurationMinutes, sessionByDay, days))
                .emotionTrend(buildEmotionTrend(emotionByDay, diaryDays))
                .userActivity(buildUserActivity(activityByDay, activeUserByDay, days))
                .build();
    }

    // ── 系统概览 ────────────────────────────────────────────────────

    private DataAnalyticsOverviewDTO.SystemOverview buildSystemOverview(SystemSnapshotRow snapshot,
                                                                        Long activeUserCount) {
        return DataAnalyticsOverviewDTO.SystemOverview.builder()
                .totalUsers(nz(snapshot.getTotalUsers()))
                .activeUsers(nz(activeUserCount))
                .totalDiaries(nz(snapshot.getTotalDiaries()))
                .todayNewDiaries(nz(snapshot.getTodayNewDiaries()))
                .totalSessions(nz(snapshot.getTotalSessions()))
                .todayNewSessions(nz(snapshot.getTodayNewSessions()))
                .avgMoodScore(round1(nz(snapshot.getAvgMoodScore())))
                .build();
    }

    // ── 咨询统计 ────────────────────────────────────────────────────

    private DataAnalyticsOverviewDTO.ConsultationStats buildConsultationStats(SystemSnapshotRow snapshot,
                                                                              Double avgDurationMinutes,
                                                                              Map<LocalDate, DailySessionRow> byDay,
                                                                              List<LocalDate> days) {
        List<DataAnalyticsOverviewDTO.DailyTrendItem> dailyTrend = days.stream()
                .map(day -> {
                    DailySessionRow row = byDay.get(day);
                    return DataAnalyticsOverviewDTO.DailyTrendItem.builder()
                            .date(day.format(DATE_FORMAT))
                            .sessionCount(row == null ? 0L : nz(row.getSessionCount()))
                            .userCount(row == null ? 0L : nz(row.getUserCount()))
                            .build();
                })
                .toList();

        return DataAnalyticsOverviewDTO.ConsultationStats.builder()
                .totalSessions(nz(snapshot.getTotalSessions()))
                .avgDurationMinutes(round1(nz(avgDurationMinutes)))
                .dailyTrend(dailyTrend)
                .build();
    }

    // ── 情绪趋势 ────────────────────────────────────────────────────

    private List<DataAnalyticsOverviewDTO.EmotionTrendItem> buildEmotionTrend(Map<LocalDate, DailyEmotionRow> byDay,
                                                                              List<LocalDate> days) {
        return days.stream()
                .map(day -> {
                    DailyEmotionRow row = byDay.get(day);
                    return DataAnalyticsOverviewDTO.EmotionTrendItem.builder()
                            .date(day.format(DATE_FORMAT))
                            .avgMoodScore(row == null ? 0d : round1(nz(row.getAvgMoodScore())))
                            .recordCount(row == null ? 0L : nz(row.getRecordCount()))
                            .build();
                })
                .toList();
    }

    // ── 用户活跃度 ──────────────────────────────────────────────────

    private List<DataAnalyticsOverviewDTO.UserActivityItem> buildUserActivity(Map<LocalDate, DailyActivityRow> activityByDay,
                                                                              Map<LocalDate, DailyActiveUserRow> activeUserByDay,
                                                                              List<LocalDate> days) {
        return days.stream()
                .map(day -> {
                    DailyActivityRow row = activityByDay.get(day);
                    DailyActiveUserRow activeUserRow = activeUserByDay.get(day);
                    return DataAnalyticsOverviewDTO.UserActivityItem.builder()
                            .date(day.format(DATE_FORMAT))
                            .activeUsers(activeUserRow == null ? 0L : nz(activeUserRow.getActiveUsers()))
                            .newUsers(row == null ? 0L : nz(row.getNewUsers()))
                            .diaryUsers(row == null ? 0L : nz(row.getDiaryUsers()))
                            .consultationUsers(row == null ? 0L : nz(row.getConsultationUsers()))
                            .build();
                })
                .toList();
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
     * 窗口右端（不含）：最后一天 + 1 天，对应 SQL 的半开区间。
     */
    private LocalDate endExclusiveOf(List<LocalDate> days) {
        return days.get(days.size() - 1).plusDays(1);
    }

    /**
     * 把聚合行按日期索引（同一日期出现多次时保留先到的）。
     */
    private <T> Map<LocalDate, T> indexByDate(List<T> rows, Function<T, LocalDate> dateGetter) {
        Map<LocalDate, T> indexed = new HashMap<>();
        if (rows == null) {
            return indexed;
        }
        for (T row : rows) {
            LocalDate date = dateGetter.apply(row);
            if (date != null) {
                indexed.putIfAbsent(date, row);
            }
        }
        return indexed;
    }

    /**
     * LocalDateTime → LocalDate（null 透传）
     */
    private LocalDate toDate(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.toLocalDate();
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
     * SQL 的空集结果（null）按 0 处理
     */
    private long nz(Long value) {
        return value == null ? 0L : value;
    }

    private double nz(Double value) {
        return value == null ? 0d : value;
    }

    /**
     * 保留一位小数
     */
    private double round1(double value) {
        return Math.round(value * 10d) / 10d;
    }
}