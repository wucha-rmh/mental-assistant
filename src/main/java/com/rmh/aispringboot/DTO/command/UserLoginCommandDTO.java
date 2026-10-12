package com.rmh.aispringboot.DTO.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserLoginCommandDTO {

    @NotBlank(message = "用户名或邮箱不能为空")
    @Size(max=100 ,message = "用户名或邮箱不能超过100")
    private String username;
    @NotBlank(message = "密码不能为空")
    @Size(max = 50,min=6,message = "密码必须在6道50个字符")
    private String password;
}
