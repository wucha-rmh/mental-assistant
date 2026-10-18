package com.rmh.aispringboot.DTO.response.analytics;

import lombok.Data;

/**
 * 看板系统概览的聚合结果（DataAnalyticsMapper.selectSystemSnapshot 的返回行）。
 *
 * <p>一次查询返回 6 个标量，避免 6 次往返。
 */
@Data
public class SystemSnapshotRow {
    // 用户总数
    private Long totalUsers;
    // 日记总数
    private Long totalDiaries;
    // 咨询会话总数
    private Long totalSessions;
    // 今日新增日记
    private Long todayNewDiaries;
    // 今日新增会话
    private Long todayNewSessions;
    // 全库平均心情评分（无日记时为 null，由 service 兜成 0）
    private Double avgMoodScore;
}