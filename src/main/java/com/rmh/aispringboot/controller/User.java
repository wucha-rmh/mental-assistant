package com.rmh.aispringboot.controller;

import cn.hutool.json.JSONUtil;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.rmh.aispringboot.DTO.command.UserLoginCommandDTO;
import com.rmh.aispringboot.DTO.command.UserRegisterCommandDTO;
import com.rmh.aispringboot.DTO.response.UserLoginResponseDTO;
import com.rmh.aispringboot.common.Result;
import com.rmh.aispringboot.mapper.UserMapper;
import com.rmh.aispringboot.service.UserService;
import com.rmh.aispringboot.util.JwtTokenUtil;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
public class User {

    @Resource
    private UserService userService;

    //用户登录接口
    @PostMapping("/login")
    public Result<UserLoginResponseDTO> login(@Valid @RequestBody UserLoginCommandDTO userLoginCommandDTO)
    {

        System.out.println(userLoginCommandDTO.getUsername());
        System.out.println(userLoginCommandDTO.getPassword());
        UserLoginResponseDTO userLoginResponseDTO = userService.login(userLoginCommandDTO);

        return Result.ok(userLoginResponseDTO);
    }
    //用户注册接口
    @PostMapping("/register")
    public Result<UserLoginResponseDTO.UserDetailResponseDTO> register(@Valid @RequestBody UserRegisterCommandDTO userRegisterCommandDTO)
    {
        UserLoginResponseDTO.UserDetailResponseDTO userRegisterResponseDTO =
                userService.register(userRegisterCommandDTO);
        return Result.ok(userRegisterResponseDTO);
    }
    //获取当前用户的接口
    //避免了重复查询校验
    @GetMapping("/current")
    public Result<UserLoginResponseDTO.UserDetailResponseDTO> getCurrentUser(){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserLoginResponseDTO.UserDetailResponseDTO user =
                (UserLoginResponseDTO.UserDetailResponseDTO) authentication.getPrincipal();
        return Result.ok(user);
    }
//    @GetMapping("/current")
//    public Result<UserLoginResponseDTO.UserDetailResponseDTO> getCurrentUser(){
//        //从token中获取用户信息
//        String currentToken = JwtTokenUtil.getCurrentToken();
//        DecodedJWT decodedJWT = JwtTokenUtil.verifyToken(currentToken);
//        Long userId = decodedJWT.getClaim("userId").asLong();
//        UserLoginResponseDTO.UserDetailxResponseDTO result  = userService.getUserById(userId);
//        return Result.ok(result);
//    }


}
