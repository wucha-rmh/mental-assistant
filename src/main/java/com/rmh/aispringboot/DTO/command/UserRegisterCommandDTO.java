package com.rmh.aispringboot.DTO.command;

import com.baomidou.mybatisplus.annotation.TableField;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
@Data
public class UserRegisterCommandDTO {
    @NotBlank(message = "用户名不能为空")
    @Size(min=3,max=50,message = "用户名在3-50个字符之间")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$",message = "用户名只能为大小写字母或下划线")
    private String username;
    //邮箱
    @NotBlank(message = "邮箱不能为空")
    @Size(max=100,message = "邮箱长度不能超过100个字符")
    @Email(message = "邮箱格式不正确")
    private String email;
    //手机号
    @Size(max=20)
    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;
    //密码
    @NotBlank(message = "密码不能为空")
    @Size(max=255,min=6,message = "密码必须在6道255个字符之间")
    private String password;
    //密码
    @NotBlank(message = "密码不能为空")
    @Size(max=255,min=6,message = "密码必须在6道255个字符之间")
    private String confirmPassword;
    //生日
    private LocalDate birthday;

    //昵称
    @Size(max = 50, message = "昵称长度不能超过50个字符")
    private String nickname;
    //性别
    private Integer gender;
    //用户类型
    @TableField("user_type")
    private Integer userType=1;
}
