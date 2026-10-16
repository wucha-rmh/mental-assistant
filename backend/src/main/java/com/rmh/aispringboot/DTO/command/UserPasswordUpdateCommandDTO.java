package com.rmh.aispringboot.DTO.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改自己的密码：必须带上原密码
 *
 * <p>「原密码」是这条接口唯一的身份再确认手段（token 只能证明「当前是这个人」，
 * 不能证明「是本人坐在这里操作」——手机被别人拿到、token 被 XSS 偷走都属于这一类）。
 * 忘记原密码的用户走不到这里，改用「联系管理员」留言，由管理员在后台重置（见
 * {@code UserService#resetPassword}）。
 *
 * <p>长度上限 32：BCrypt 只取前 72 字节，限制长度可以避免「超长密码后半段不参与校验」的错觉。
 */
@Data
public class UserPasswordUpdateCommandDTO {

    @NotBlank(message = "请输入原密码")
    private String oldPassword;

    @NotBlank(message = "请输入新密码")
    @Size(min = 6, max = 32, message = "新密码长度需在6-32个字符之间")
    private String newPassword;

    @NotBlank(message = "请再次输入新密码")
    private String confirmPassword;
}
