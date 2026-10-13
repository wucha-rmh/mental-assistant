package com.rmh.aispringboot.controller;

import cn.hutool.json.JSONUtil;
import com.rmh.aispringboot.DTO.command.UserLoginCommandDTO;
import com.rmh.aispringboot.DTO.command.UserRegisterCommandDTO;
import com.rmh.aispringboot.DTO.response.UserLoginResponseDTO;
import com.rmh.aispringboot.common.Result;
import com.rmh.aispringboot.mapper.UserMapper;
import com.rmh.aispringboot.service.UserService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
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
    @GetMapping("/current")
    public Result<UserLoginResponseDTO.UserDetailResponseDTO> getCurrentUser(){
        //从token中获取用户信息
        return null;
    }


}
