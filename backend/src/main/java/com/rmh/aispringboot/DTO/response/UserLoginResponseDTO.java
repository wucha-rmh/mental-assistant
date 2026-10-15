package com.rmh.aispringboot.DTO.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class UserLoginResponseDTO {
    private String token;
    private String roleType;
    private UserDetailResponseDTO userInfo;

    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    public static class UserDetailResponseDTO
    {
        //用户ID
        private Long id;
        // 用户名
        private String username;
        // 邮箱
        private String email;
        // 昵称
        private String nickname;
        // 头像
        private String avatar;
        // 手机号
        private String phone;
        // 性别 0:未知 1:男 2:女
        private Integer gender;
        // 性别显示名称
        private String genderDisplayName;
        // 生日
        private LocalDate birthday;
        // 用户类型 1:普通用户 2:管理员
        private Integer userType;
        // 用户类型显示名称
        private String userTypeDisplayName;
        // 状态 0:禁用 1:正常
        private Integer status;
        // 用户状态显示名称
        private String statusDisplayName;
        // 显示名称
        private String displayName;
        // 创建时间
        private LocalDateTime createdAt;
        // 更新时间
        private LocalDateTime updatedAt;
    }
}
