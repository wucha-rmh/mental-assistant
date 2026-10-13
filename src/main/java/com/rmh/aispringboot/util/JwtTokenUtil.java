package com.rmh.aispringboot.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.rmh.aispringboot.config.JwtConfig;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Date;

@Component
public class JwtTokenUtil implements ApplicationContextAware {
    private static ApplicationContext applicationContext;
    private static final String ISSUER = "mental-health-assistant";
    //用于在静态工具类中获取spring容器管理的bean
    @Override
    public void setApplicationContext(ApplicationContext applicationContext)
    {
        JwtTokenUtil.applicationContext = applicationContext;
    }

        private static JwtConfig getJwtConfig()
    {
        return applicationContext.getBean(JwtConfig.class);
    }
    //生成token的逻辑
    public static String generateToken(Long userId,String username,Integer roleType)
    {
        try{
            //获取jwt配置
            JwtConfig jwtConfig = getJwtConfig();
            //生成签名算法
            Algorithm algorithm = Algorithm.HMAC256(jwtConfig.getSecret());
            //生成过期时间
            Date expiredDate = new Date(System.currentTimeMillis()+jwtConfig.getExpiration());
            return JWT.create()
                    .withClaim("userId",userId)
                    .withClaim("username",username)
                    .withClaim("roleType",roleType)
                    .withExpiresAt(expiredDate)
                    .withIssuedAt(new Date())
                    .withIssuer(ISSUER)
                    .sign(algorithm);

        }
        catch (Exception e)
        {
            throw new RuntimeException("生成Token失败，原因是："+ e);
        }
    }
    //提取token
    public static String extractTokenFromRequest(HttpServletRequest request)
    {
        if(request==null)
        {
            return null;
        }
        String tokenHeader = request.getHeader("token");
        if(StringUtils.hasText(tokenHeader))
        {
            return tokenHeader;
        }
        return null;
    }

    //验证token
    public static TokenVerificationResult validateToken(String token)
    {
        DecodedJWT decodedJWT = verifyToken(token);
        Long userId = decodedJWT.getClaim("userId").asLong();
        String username = decodedJWT.getClaim("username").asString();
        //角色类型
        Integer roleType = null;
        try{
            roleType=decodedJWT.getClaim("roleType").asInt();
        }catch (Exception e){
            String roleType1 = decodedJWT.getClaim("roleType").asString();
            if(StringUtils.hasText(roleType1))
            {
                roleType= Integer.valueOf(roleType1);
            }
        }
        if(userId!=null&&StringUtils.hasText(username)&&roleType!=null)
        {
            return new TokenVerificationResult(userId,username,roleType,true);
        }
        return null;
    }

    //验证token的有效性
    public static DecodedJWT verifyToken(String token)
    {
        if(!StringUtils.hasText(token))
        {
            throw new JWTVerificationException("Token不能为空");
        }
        //Token解码
        JwtConfig jwtConfig= getJwtConfig();
        Algorithm algorithm = Algorithm.HMAC256(jwtConfig.getSecret());
        JWTVerifier verifier=  JWT.require(algorithm)
                .withIssuer(ISSUER)
                .build();
        return verifier.verify(token);
    }
   //token验证结果封装类
    @Getter
    public static class TokenVerificationResult {
        private final Long userId;
        private final String username;
        private final Integer roleType;
        private final boolean isValid;

       public TokenVerificationResult(Long userId, String username, Integer roleType, boolean isValid) {
           this.userId = userId;
           this.username = username;
           this.roleType = roleType;
           this.isValid = isValid;
       }
   }

}
