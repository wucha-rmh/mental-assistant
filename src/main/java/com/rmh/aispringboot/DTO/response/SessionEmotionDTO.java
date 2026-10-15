package com.rmh.aispringboot.DTO.response;

import lombok.Builder;
import lombok.Data;

import java.util.Collections;
import java.util.List;

/**
 * 会话情绪分析结果，字段对齐前端情绪面板
 *
 * <p>后端目前尚未实现情绪分析逻辑，无数据时统一返回 {@link #defaultValue()}。
 */
@Data
@Builder
public class SessionEmotionDTO {

    /** 主要情绪 */
    private String primaryEmotion;

    /** 情绪强度 0-100 */
    private Integer emotionScore;

    /** 是否负面情绪 */
    private Boolean isNegative;

    /** 风险等级：0 正常 / 1 关注 / 2 预警 / 3 危机 */
    private Integer riskLevel;

    /** 疏导建议 */
    private String suggestion;

    /** 改善建议列表 */
    private List<String> improvementSuggestions;

    /** 风险描述 */
    private String riskDescription;

    /**
     * 默认值：尚未分析时的兜底返回，保证前端情绪面板不报错
     */
    public static SessionEmotionDTO defaultValue() {
        return SessionEmotionDTO.builder()
                .primaryEmotion("中性")
                .emotionScore(50)
                .isNegative(false)
                .riskLevel(0)
                .suggestion("")
                .improvementSuggestions(Collections.emptyList())
                .riskDescription("")
                .build();
    }
}
