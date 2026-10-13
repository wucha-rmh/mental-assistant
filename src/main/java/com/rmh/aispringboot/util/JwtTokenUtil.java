package com.rmh.aispringboot.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.rmh.aispringboot.config.JwtConfig;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.util.Date;

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
    public static String generateToken(String userId,String username,Integer roleType)
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
}
