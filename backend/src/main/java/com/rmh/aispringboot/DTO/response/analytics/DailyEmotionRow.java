package com.rmh.aispringboot.DTO.response.analytics;

import lombok.Data;

import java.time.LocalDate;

/**
 * 情绪趋势的单日聚合结果（DataAnalyticsMapper.selectDailyEmotions 的返回行）。
 */
@Data
public class DailyEmotionRow {
    // 日期
    private LocalDate statDate;
    // 当日日记条数
    private Long recordCount;
    // 当日平均心情评分（当日无日记时为 null，由 service 兜成 0）
    private Double avgMoodScore;
}