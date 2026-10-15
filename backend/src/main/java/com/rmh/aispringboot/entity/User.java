package com.rmh.aispringboot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.rmh.aispringboot.enumClass.UserStatus;
import com.rmh.aispringboot.enumClass.UserType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.cglib.core.Local;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;

//用户实体类
@Data
@TableName("user")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
    //用户ID
    @TableId(type = IdType.AUTO)
    private Long id;
    //用户名
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
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;
    //密码
    @NotBlank(message = "密码不能为空")
    @Size(max=255,min=6,message = "密码必须在6道255个字符之间")
    private String password;
    //昵称
    @Size(max = 50, message = "昵称长度不能超过50个字符")
    private String nickname;
    //头像URL
    @Size(max=255,message = "头像路径不能超过255字符")
    private String avatar;
    //性别
    private Integer gender;
    //生日
    private LocalDate birthday;
    //用户类型
    @TableField("user_type")
    private Integer userType;
    //状态
    private Integer status;
    //创建时间
    @TableField("created_at")
    private LocalDateTime createdAt;
    //修改时间
    @TableField("updated_at")
    private LocalDateTime updatedAt;

//    /**
//     * 是否为普通用户
//     */
//    public boolean isUser() {
//        return UserType.USER.getCode().equals(this.userType);
//    }

    /**
     * 是否为正常状态
     */
    public boolean isActive() {
        return UserStatus.NORMAL.getCode().equals(this.status);
    }

    /**
     * 是否被禁用
     */
    public boolean isDisabled() {
        return UserStatus.DISABLED.getCode().equals(this.status);
    }

    /**
     * 获取显示名称（优先显示昵称，否则显示用户名）
     */
    public String getDisplayName() {
        return nickname != null && !nickname.trim().isEmpty() ? nickname : username;
    }

    /**
     * 获取用户类型显示名称
     */
    public String getUserTypeDisplayName() {
        try {
            return UserType.fromCode(userType).getDescription();
        } catch (IllegalArgumentException e) {
            return "未知";
        }
    }

    /**
     * 获取用户状态显示名称
     */
    public String getStatusDisplayName() {
        try {
            return UserStatus.fromCode(status).getDescription();
        } catch (IllegalArgumentException e) {
            return "未知";
        }
    }
}

