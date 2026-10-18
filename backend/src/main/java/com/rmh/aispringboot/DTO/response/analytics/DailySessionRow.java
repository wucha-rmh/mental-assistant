package com.rmh.aispringboot.DTO.response.analytics;

import lombok.Data;

import java.time.LocalDate;

/**
 * 咨询趋势的单日聚合结果（DataAnalyticsMapper.selectDailySessions 的返回行）。
 */
@Data
public class DailySessionRow {
    // 日期
    private LocalDate statDate;
    // 当日会话数
    private Long sessionCount;
    // 当日参与用户数（去重）
    private Long userCount;
}