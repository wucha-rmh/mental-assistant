package com.rmh.aispringboot.DTO.command;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户端「记录心情」的请求体
 *
 * <p>字段与前端 {@code emotionDiary.vue} 的 {@code diaryForm} 一致；
 * {@code userId} 一律取自登录态，**不接受前端传入**（防越权写别人的日记）。
 */
@Data
public class EmotionDiaryCreateCommandDTO {

    /** 日记日期，格式 {@code yyyy-MM-dd}；为空时用服务器当天 */
    private String diaryDate;

    @NotNull(message = "请选择心情评分")
    @Min(value = 1, message = "心情评分范围是 1-10")
    @Max(value = 10, message = "心情评分范围是 1-10")
    private Integer moodScore;

    @Size(max = 2000, message = "日记内容最多2000个字符")
    private String diaryContent;
}
