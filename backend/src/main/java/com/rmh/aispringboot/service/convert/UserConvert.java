package com.rmh.aispringboot.service.convert;

import com.rmh.aispringboot.DTO.command.UserRegisterCommandDTO;
import com.rmh.aispringboot.DTO.response.UserLoginResponseDTO;
import com.rmh.aispringboot.entity.User;
import com.rmh.aispringboot.enumClass.UserStatus;
import com.rmh.aispringboot.enumClass.UserType;

import java.time.LocalDateTime;

public class UserConvert {
    //将用户实体转换为返回DTO
    public static UserLoginResponseDTO.UserDetailResponseDTO entityToDetaiResponse(User user)
    {
        return UserLoginResponseDTO.UserDetailResponseDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .phone(user.getPhone())
                .gender(user.getGender())
                .genderDisplayName(getGenderDisplayName(user.getGender()))
                .birthday(user.getBirthday())
                .userType(user.getUserType())
                .userTypeDisplayName(user.getUserTypeDisplayName())
                .status(user.getStatus())
                .statusDisplayName(user.getStatusDisplayName())
                .displayName(user.getDisplayName())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
    public static  UserLoginResponseDTO entityToResponseDTO(String token,UserLoginResponseDTO.UserDetailResponseDTO userInfo)
    {
        return UserLoginResponseDTO.builder()
                .userInfo(userInfo)
                .token(token)
                .roleType(userInfo.getUserType().toString())
                .build();

    }


    public static User registerCommandToEntity(UserRegisterCommandDTO commandDTO, String encodedpassword)
    {
        return User.builder()
                .username(commandDTO.getUsername())
                .email(commandDTO.getEmail())
                .password(encodedpassword)
                .nickname(commandDTO.getNickname())
                .phone(commandDTO.getPhone())
                .gender(commandDTO.getGender())
                .birthday(commandDTO.getBirthday())
                // ⚠️ 注册接口一律创建普通用户，userType 不接受客户端传值（防自助提权）
                .userType(UserType.USER.getCode())
                .status(UserStatus.NORMAL.getCode())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
    /**
     * 获取性别显示名称
     * @param gender 性别代码
     * @return 性别显示名称
     */
    private static String getGenderDisplayName(Integer gender) {
        if (gender == null) {
            return "未知";
        }
        switch (gender) {
            case 1:
                return "男";
            case 2:
                return "女";
            default:
                return "未知";
        }
    }
}
