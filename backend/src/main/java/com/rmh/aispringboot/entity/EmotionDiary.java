package com.rmh.aispringboot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 情绪日记
 *
 * <p>(user_id, diary_date) 上有唯一键，同一用户同一天只允许一条。
 */
@Data
@TableName("emotion_diary")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EmotionDiary {
    // 日记ID
    @TableId(type = IdType.AUTO)
    private Long id;

    // 所属用户ID
    @TableField("user_id")
    private Long userId;

    // 日记日期
    @TableField("diary_date")
    private LocalDate diaryDate;

    // 心情评分 1-10
    @TableField("mood_score")
    private Integer moodScore;

    // 日记正文（今日感想）
    @TableField("diary_content")
    private String diaryContent;

    // 创建时间
    @TableField("created_at")
    private LocalDateTime createdAt;

    // 更新时间
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
