package com.rmh.aispringboot.util;

import com.rmh.aispringboot.DTO.response.UserLoginResponseDTO;
import com.rmh.aispringboot.common.ResultCode;
import com.rmh.aispringboot.config.SecurityConfig;
import com.rmh.aispringboot.enumClass.UserStatus;
import com.rmh.aispringboot.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class JwtAuthenticationFilter extends OncePerRequestFilter {
    @Resource
    private UserService userService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request)
    {
        String requestURI = request.getRequestURI();
        //检查是否为公开路径
        return SecurityConfig.isPublicPATH(requestURI);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain)throws ServletException, IOException{
        //1.提取JWT token
        String token=JwtTokenUtil.extractTokenFromRequest(request);
        if(!StringUtils.hasText(token)){
            //没带 token：访问未授权
            clearSecurityContext();
            ResponseUtil.writeError(response, ResultCode.ACCESS_UNAUTHORIZED);
            return;
        }

        //2.验证 Token、查用户、写入认证信息
        //   ⚠️ 这两步都可能抛异常：validateToken 在「签名不匹配 / 已过期 / issuer 不符」时抛
        //   JWTVerificationException，getUserById 对「已被删除的用户」抛 BusinessException。
        //   都不能漏给 Tomcat —— 漏出去就变成 HTTP 500 + Spring 默认错误体，前端拿不到 msg，
        //   只会弹「服务器开小差了」且不会自动回登录页（密钥轮换后旧 token 必然走到这里）。
        try {
            JwtTokenUtil.TokenVerificationResult validationResult = JwtTokenUtil.validateToken(token);
            if(validationResult==null||!validationResult.isValid()){
                clearSecurityContext();
                ResponseUtil.writeError(response, ResultCode.TOKEN_INVALID);
                return;
            }

            //3.查询用户信息并验证用户状态
            UserLoginResponseDTO.UserDetailResponseDTO user=
                    userService.getUserById(validationResult.getUserId());
            if(user==null||!UserStatus.NORMAL.getCode().equals(user.getStatus())){
                //用户不存在或已被禁用（禁用立即生效就是靠这一句）
                clearSecurityContext();
                ResponseUtil.writeError(response,ResultCode.TOKEN_ACCESS_FORBIDDEN);
                return;
            }

            //4.创建Spring Security认证对象
            List<SimpleGrantedAuthority> simpleGrantedAuthorities = Collections.singletonList(
                    new SimpleGrantedAuthority("ROLE_" + validationResult.getRoleType()));
            //5.创建UsernamePasswordAuthenticationToken
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(user,
                    null,
                    simpleGrantedAuthorities);
            //设置认证信息到Spring Security上下文
            SecurityContextHolder.getContext().setAuthentication(auth);

            //将token存储到请求属性中
            request.setAttribute("jwtToken", token);
        } catch (Exception e) {
            //token 无效 / 用户已被删除等，统一按 A0230 处理（前端会清掉本地 token 并回登录页）
            clearSecurityContext();
            ResponseUtil.writeError(response, ResultCode.TOKEN_INVALID);
            return;
        }

        //继续过滤器链
        chain.doFilter(request,response);
    }

    //清理Spring Security 上下文
    private void clearSecurityContext()
    {
        SecurityContextHolder.clearContext();
    }
}
