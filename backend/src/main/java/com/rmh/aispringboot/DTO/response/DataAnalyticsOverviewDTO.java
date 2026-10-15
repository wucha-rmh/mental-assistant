package com.rmh.aispringboot.DTO.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 数据看板总览
 *
 * <p>前端 {@code dashboard.vue} 会无条件对 {@code emotionTrend}、{@code consultationStats.dailyTrend}、
 * {@code userActivity} 做 {@code .map}，所以这三个字段**必须存在且是数组**，缺失会让页面直接抛错。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DataAnalyticsOverviewDTO {
    // 系统概览（四个统计卡片）
    private SystemOverview systemOverview;
    // 咨询统计
    private ConsultationStats consultationStats;
    // 情绪趋势
    private List<EmotionTrendItem> emotionTrend;
    // 用户活跃度
    private List<UserActivityItem> userActivity;

    /**
     * 系统概览
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SystemOverview {
        // 用户总数
        private long totalUsers;
        // 活跃用户数（有日记或咨询记录的去重用户数）
        private long activeUsers;
        // 日记总数
        private long totalDiaries;
        // 今日新增日记
        private long todayNewDiaries;
        // 咨询会话总数
        private long totalSessions;
        // 今日新增会话
        private long todayNewSessions;
        // 平均心情评分
        private double avgMoodScore;
    }

    /**
     * 咨询统计
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ConsultationStats {
        // 会话总数
        private long totalSessions;
        // 平均会话时长（分钟）
        private double avgDurationMinutes;
        // 每日咨询趋势
        private List<DailyTrendItem> dailyTrend;
    }

    /**
     * 每日咨询趋势项
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DailyTrendItem {
        // 日期 yyyy-MM-dd
        private String date;
        // 当日会话数
        private long sessionCount;
        // 当日参与用户数
        private long userCount;
    }

    /**
     * 情绪趋势项
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class EmotionTrendItem {
        // 日期 yyyy-MM-dd
        private String date;
        // 当日平均心情评分
        private double avgMoodScore;
        // 当日日记条数
        private long recordCount;
    }

    /**
     * 用户活跃度项
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class UserActivityItem {
        // 日期 yyyy-MM-dd
        private String date;
        // 当日活跃用户数（有日记或咨询）
        private long activeUsers;
        // 当日新增用户数
        private long newUsers;
        // 当日记录日记的用户数
        private long diaryUsers;
        // 当日发起咨询的用户数
        private long consultationUsers;
    }
}
