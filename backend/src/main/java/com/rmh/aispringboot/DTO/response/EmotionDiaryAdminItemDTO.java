package com.rmh.aispringboot.DTO.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 情绪日记（管理后台列表/详情）
 *
 * <p>前端约定：{@code aiEmotionAnalysis} 必须是**合法的 JSON 字符串或 null**，
 * 因为前端直接 {@code JSON.parse} 且没有 try/catch。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EmotionDiaryAdminItemDTO {
    // 日记ID
    private Long id;
    // 用户ID
    private Long userId;
    // 用户名
    private String username;
    // 昵称（前端优先展示）
    private String nickname;
    // 日记日期
    private LocalDate diaryDate;
    // 心情评分 1-10
    private Integer moodScore;
    // 日记正文（今日感想）
    private String diaryContent;
    // AI 情绪分析（JSON 字符串，非法数据会被置为 null 以免前端解析崩溃）
    private String aiEmotionAnalysis;
    // AI 分析更新时间
    private LocalDateTime aiAnalysisUpdatedAt;
    // 创建时间
    private LocalDateTime createdAt;
    // 更新时间
    private LocalDateTime updatedAt;
}
