package com.rmh.aispringboot.DTO.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理员重置某个用户的密码（用户忘记密码时用）
 *
 * <p>不需要用户的原密码（用户已经想不起来了，这正是管理员介入的原因），
 * 也不需要管理员再输入自己的密码——调用者已经过 {@code SecurityUserUtil.requireAdmin()}。
 * 管理员**不能**用这条接口改自己的密码：自己的密码必须走「修改密码」并填原密码。
 */
@Data
public class UserPasswordResetCommandDTO {

    @NotBlank(message = "请输入新密码")
    @Size(min = 6, max = 32, message = "新密码长度需在6-32个字符之间")
    private String newPassword;
}
