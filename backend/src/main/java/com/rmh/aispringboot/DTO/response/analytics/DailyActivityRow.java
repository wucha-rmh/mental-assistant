package com.rmh.aispringboot.DTO.response.analytics;

import lombok.Data;

import java.time.LocalDate;

/**
 * 用户活跃度的单日聚合结果（DataAnalyticsMapper.selectDailyActivities 的返回行）。
 *
 * <p>只含「三个计数」；当日活跃用户数因需跨表去重，单独由
 * DataAnalyticsMapper.selectDailyActiveUsers 返回。
 */
@Data
public class DailyActivityRow {
    // 日期
    private LocalDate statDate;
    // 当日记录日记的用户数（去重）
    private Long diaryUsers;
    // 当日发起咨询的用户数（去重）
    private Long consultationUsers;
    // 当日新增用户数
    private Long newUsers;
}