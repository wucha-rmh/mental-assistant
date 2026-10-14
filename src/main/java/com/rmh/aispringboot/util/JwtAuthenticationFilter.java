package com.rmh.aispringboot.util;

import cn.hutool.json.JSONUtil;
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
    private  UserService userService;
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
            //获取请求URL和方法
        StringBuffer requestURL = request.getRequestURL();
        String method = request.getMethod();
        System.out.println(requestURL.toString());
        System.out.println(method);
        //1.提取JWT token
        String token=JwtTokenUtil.extractTokenFromRequest(request);
        if(StringUtils.hasText(token)){
            //2.验证 Token并获取用户信息
            JwtTokenUtil.TokenVerificationResult validationResult = JwtTokenUtil.validateToken(token);
            if(validationResult!=null&&validationResult.isValid()){
                //3.查询用户信息并验证用户状态
                UserLoginResponseDTO.UserDetailResponseDTO user=
                        userService.getUserById(validationResult.getUserId());
                if(user!=null&&UserStatus.NORMAL.getCode().equals(user.getStatus())) {
                    //用户状态正常，继续处理请求
                    //4.创建Spring Security认证对象
                    List<SimpleGrantedAuthority> simpleGrantedAuthorities = Collections.singletonList(
                            new SimpleGrantedAuthority("ROLE_" + validationResult.getRoleType()));
                    //5.创建UsernamePasswordAuthenticationToken
                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(user,
                            null,
                            simpleGrantedAuthorities);
                    //设置认证信息道Spring Security上下文
                    SecurityContextHolder.getContext().setAuthentication(auth);

                    //将token存储道请求属性中
                    request.setAttribute("jwtToken", token);

                }else
                {
                    //清理上下文
                    clearSecurityContext();
                    ResponseUtil.writeError(response,ResultCode.TOKEN_ACCESS_FORBIDDEN);
                    return;
                }

            }
            else{
                //清理上下文
                clearSecurityContext();
                ResponseUtil.writeError(response,ResultCode.TOKEN_INVALID);
                return;
            }

        }else{
            //清理上下文
            clearSecurityContext();
            ResponseUtil.writeError(response, ResultCode.ACCESS_UNAUTHORIZED);
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
