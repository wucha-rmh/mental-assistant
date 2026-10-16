package com.rmh.aispringboot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户留言（给管理员的消息）
 *
 * <p>表结构见 {@code db/schema.sql} 与 {@code db/migrations/20261016-user-feedback.sql}。
 * 一个用户可以留多条；管理员回复后 {@code status} 由 0（待处理）变 1（已回复）。
 */
@Data
@TableName("user_feedback")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserFeedback {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 留言用户ID（未登录留言为 null） */
    @TableField("user_id")
    private Long userId;

    /** 未登录留言时用户自己填写的用户名/称呼 */
    @TableField("guest_name")
    private String guestName;

    /** 留言类型 1:忘记密码/无法登录 2:账号问题 3:功能建议 4:其它 */
    @TableField("feedback_type")
    private Integer feedbackType;

    /** 留言内容 */
    private String content;

    /** 联系方式（可选） */
    private String contact;

    /** 状态 0:待处理 1:已回复 */
    private Integer status;

    /** 管理员回复内容 */
    @TableField("admin_reply")
    private String adminReply;

    /** 回复的管理员ID（后台可看出是谁处理的） */
    @TableField("reply_admin_id")
    private Long replyAdminId;

    /** 回复时间 */
    @TableField("replied_at")
    private LocalDateTime repliedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
