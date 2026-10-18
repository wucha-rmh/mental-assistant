package com.rmh.aispringboot.DTO.response.analytics;

import lombok.Data;

import java.time.LocalDate;

/**
 * 单日活跃用户数（DataAnalyticsMapper.selectDailyActiveUsers 的返回行）。
 *
 * <p>「活跃」= 当日有日记或有咨询会话，按 user_id 跨两张表去重。
 */
@Data
public class DailyActiveUserRow {
    // 日期
    private LocalDate statDate;
    // 当日活跃用户数（日记用户与咨询用户取并集后去重）
    private Long activeUsers;
}