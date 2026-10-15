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
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/user")
public class User {

    @Resource
    private UserService userService;

    //用户登录接口
    @PostMapping("/login")
    public Result<UserLoginResponseDTO> login(@Valid @RequestBody UserLoginCommandDTO userLoginCommandDTO)
    {
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

    /**
     * 上传/更换当前登录用户的头像
     *
     * <p>multipart/form-data，字段名 {@code file}；返回更新后的用户信息，
     * 其中 {@code avatar} 是项目内的相对路径 {@code /files/bussiness/user_avatar/...}。
     * 未换过头像的用户 {@code avatar} 为 null，前端回退到本地默认图。
     */
    @PostMapping("/avatar")
    public Result<UserLoginResponseDTO.UserDetailResponseDTO> uploadAvatar(
            @RequestParam("file") MultipartFile file) {
        return Result.ok(userService.updateAvatar(file));
    }

    /**
     * 退出登录
     *
     * <p>JWT 是无状态的，服务端没有 session 可销毁，本接口只用于告知前端可以清理本地凭证。
     * 因此它被放进白名单 —— 即使 token 已过期也能正常返回，保证前端在任何情况下都能完成退出。
     */
    @PostMapping("/logout")
    public Result<Void> logout() {
        return Result.ok();
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
