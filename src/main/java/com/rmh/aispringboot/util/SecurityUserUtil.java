package com.rmh.aispringboot.util;

import com.rmh.aispringboot.DTO.response.UserLoginResponseDTO;
import com.rmh.aispringboot.exception.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 当前登录用户工具。
 *
 * <p>principal 由 {@code JwtAuthenticationFilter} 写入 SecurityContext，
 * 这里统一取出，避免每个 Controller 重复写一遍。
 */
public final class SecurityUserUtil {

    /** 管理员用户类型 */
    private static final int USER_TYPE_ADMIN = 2;

    private SecurityUserUtil() {
    }

    /**
     * 取当前登录用户，未登录时抛业务异常
     */
    public static UserLoginResponseDTO.UserDetailResponseDTO currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !(authentication.getPrincipal() instanceof UserLoginResponseDTO.UserDetailResponseDTO user)) {
            throw new BusinessException("用户未登录");
        }
        return user;
    }

    /**
     * 取当前登录用户并校验管理员身份，非管理员抛业务异常
     */
    public static UserLoginResponseDTO.UserDetailResponseDTO requireAdmin() {
        UserLoginResponseDTO.UserDetailResponseDTO user = currentUser();
        if (!isAdmin(user)) {
            throw new BusinessException("无权访问，该接口仅管理员可用");
        }
        return user;
    }

    /**
     * 是否为管理员（userType = 2）
     */
    public static boolean isAdmin(UserLoginResponseDTO.UserDetailResponseDTO user) {
        return user != null && user.getUserType() != null && user.getUserType() == USER_TYPE_ADMIN;
    }
}
