package com.rmh.aispringboot.DTO.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户留言（用户端「我的留言」与后台「留言管理」共用）
 *
 * <p>用户端只会拿到自己的留言（服务端按当前登录用户过滤），
 * {@code username}/{@code nickname} 是给后台表格显示「是谁留的」用的。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserFeedbackDTO {

    private Long id;

    private Long userId;

    /** 留言人用户名（登录用户；未登录留言时为 null） */
    private String username;

    /** 留言人昵称（后台列表用，可能为空） */
    private String nickname;

    /** 未登录留言时用户自己填写的用户名/称呼（userId 为 null 时用它显示「是谁留的」） */
    private String guestName;

    /** 留言类型 1:忘记密码/无法登录 2:账号问题 3:功能建议 4:其它 */
    private Integer feedbackType;

    /** 留言类型显示名（前端直接渲染，不用自己查字典） */
    private String feedbackTypeName;

    private String content;

    /** 用户留的联系方式，可为空 */
    private String contact;

    /** 0:待处理 1:已回复 */
    private Integer status;

    /** 状态显示名 */
    private String statusName;

    /** 管理员回复内容，未回复时为 null */
    private String adminReply;

    /** 回复时间，未回复时为 null */
    private LocalDateTime repliedAt;

    private LocalDateTime createdAt;
}
