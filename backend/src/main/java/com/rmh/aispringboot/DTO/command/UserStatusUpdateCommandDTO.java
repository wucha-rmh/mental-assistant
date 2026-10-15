package com.rmh.aispringboot.DTO.command;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 管理员修改用户状态的请求体
 *
 * <p>{@code status}：0 = 禁用，1 = 正常（见 {@code enumClass/UserStatus}）。
 * 只有这两个值会被接受，非法值由 Service 转成业务异常。
 */
@Data
public class UserStatusUpdateCommandDTO {

    @NotNull(message = "状态不能为空")
    private Integer status;
}
