package com.rmh.aispringboot.controller;

import com.rmh.aispringboot.DTO.command.UserLoginCommandDTO;
import com.rmh.aispringboot.common.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
public class User {
    @PostMapping("/login")
    public Result<String> login(@Valid @RequestBody UserLoginCommandDTO userLoginCommandDTO)
    {

        System.out.println(userLoginCommandDTO.getUsername());
        System.out.println(userLoginCommandDTO.getPassword());
        return null;
    }
}
