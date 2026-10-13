package com.rmh.aispringboot.controller;

import com.rmh.aispringboot.DTO.command.UserLoginCommandDTO;
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

    @PostMapping("/login")
    public Result<UserLoginResponseDTO> login(@Valid @RequestBody UserLoginCommandDTO userLoginCommandDTO)
    {

        System.out.println(userLoginCommandDTO.getUsername());
        System.out.println(userLoginCommandDTO.getPassword());
        Result<UserLoginResponseDTO> result = userService.login(userLoginCommandDTO);

        return result;
    }
}
