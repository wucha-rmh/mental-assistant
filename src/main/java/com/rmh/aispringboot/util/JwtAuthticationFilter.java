package com.rmh.aispringboot.util;

import com.rmh.aispringboot.common.ResultCode;
import com.rmh.aispringboot.config.SecurityConfig;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class JwtAuthticationFilter extends OncePerRequestFilter {
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

            }
            else{
                //清理上下文
                clearSecurityContext();
                ResponseUtil.writeError(response,ResultCode.TOKEN_INVALID);
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
