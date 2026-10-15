package com.rmh.aispringboot.DTO.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 情绪日记（管理后台列表/详情）
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
    // 创建时间
    private LocalDateTime createdAt;
    // 更新时间
    private LocalDateTime updatedAt;
}
