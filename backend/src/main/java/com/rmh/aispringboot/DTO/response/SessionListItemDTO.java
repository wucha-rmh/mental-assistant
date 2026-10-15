package com.rmh.aispringboot.DTO.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话列表项
 *
 * <p>字段与前端会话列表页（用户端 consultation.vue / 后台 consultations.vue）实际使用的字段对齐。
 */
@Data
@Builder
public class SessionListItemDTO {

    /** 会话 ID —— 裸 id，前端据此调用详情 / 删除 / 情绪接口 */
    private Long id;

    /** 会话标题 */
    private String sessionTitle;

    /** 开始时间 */
    private LocalDateTime startedAt;

    /** 最后一条消息内容（列表预览） */
    private String lastMessageContent;

    /** 消息条数 */
    private Integer messageCount;

    /** 对话时长（分钟）：开始时间 → 最后一条消息时间 */
    private Long durationMinutes;

    /** 最后一条消息时间（后台页面使用） */
    private LocalDateTime lastMessageTime;

    /** 用户昵称（仅管理员查询时填充） */
    private String userNickname;
}
