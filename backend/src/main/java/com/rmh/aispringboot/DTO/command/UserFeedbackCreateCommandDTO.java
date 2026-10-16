package com.rmh.aispringboot.DTO.command;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户给管理员留言（忘了密码、账号问题、功能建议…）
 *
 * <p>留言不要求填联系方式：站内本身就能看到管理员的回复。
 * {@code contact} 只在用户希望管理员用其它方式联系他时填（手机号 / 邮箱 / 微信号）。
 */
@Data
public class UserFeedbackCreateCommandDTO {

    /** 留言类型 1:忘记密码/无法登录 2:账号问题 3:功能建议 4:其它 */
    @NotNull(message = "请选择留言类型")
    @Min(value = 1, message = "留言类型取值不正确")
    @Max(value = 4, message = "留言类型取值不正确")
    private Integer feedbackType;

    @NotBlank(message = "留言内容不能为空")
    @Size(max = 1000, message = "留言内容最多1000个字符")
    private String content;

    /** 联系方式（可选，最多100字符） */
    @Size(max = 100, message = "联系方式最多100个字符")
    private String contact;

    /**
     * 未登录用户填写的用户名/称呼（必填项由前端控制）
     *
     * <p>已登录用户提交时服务端会忽略它（直接用 token 里的 userId）；
     * 只有白名单接口 {@code POST /api/feedback/guest}（忘记密码、登不进来的场景）会用到。
     */
    @Size(max = 50, message = "用户名最多50个字符")
    private String guestUsername;
}
