package com.rmh.aispringboot.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rmh.aispringboot.DTO.command.UserLoginCommandDTO;
import com.rmh.aispringboot.DTO.command.UserPasswordResetCommandDTO;
import com.rmh.aispringboot.DTO.command.UserPasswordUpdateCommandDTO;
import com.rmh.aispringboot.DTO.command.UserProfileUpdateCommandDTO;
import com.rmh.aispringboot.DTO.command.UserRegisterCommandDTO;
import com.rmh.aispringboot.DTO.command.UserStatusUpdateCommandDTO;
import com.rmh.aispringboot.DTO.response.UserLoginResponseDTO;
import com.rmh.aispringboot.common.Result;
import com.rmh.aispringboot.service.UserService;
import com.rmh.aispringboot.util.SecurityUserUtil;
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

    /**
     * 用户列表（管理员）
     *
     * <p>分页参数兼容两套命名：用户端 {@code pageNum/pageSize}、后台 {@code currentPage/size}。
     * 返回体是 {@code UserDetailResponseDTO}（无密码字段），含 {@code status}/{@code statusDisplayName}
     * 与 {@code userType}/{@code userTypeDisplayName} 供表格渲染。
     */
    @GetMapping("/admin/page")
    public Result<Page<UserLoginResponseDTO.UserDetailResponseDTO>> pageUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer userType,
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) Integer currentPage,
            @RequestParam(required = false) Integer size) {

        SecurityUserUtil.requireAdmin();

        int page = pageNum != null ? pageNum : (currentPage != null ? currentPage : 1);
        int limit = pageSize != null ? pageSize : (size != null ? size : 10);

        return Result.ok(userService.pageUsers(keyword, status, userType, page, limit));
    }

    /**
     * 修改用户状态（管理员）：0 = 禁用，1 = 正常
     *
     * <p>禁用立即生效（登录被拦 + 已签发 token 被 {@code JwtAuthenticationFilter} 拒绝）；
     * 管理员不能改自己的状态。
     */
    @PutMapping("/admin/{id}/status")
    public Result<UserLoginResponseDTO.UserDetailResponseDTO> updateUserStatus(
            @PathVariable("id") Long id,
            @Valid @RequestBody UserStatusUpdateCommandDTO commandDTO) {

        SecurityUserUtil.requireAdmin();
        return Result.ok(userService.updateStatus(id, commandDTO.getStatus()));
    }

    /**
     * 修改个人资料（当前登录用户）：昵称 / 性别 / 生日
     *
     * <p>用户名/邮箱/手机号不在这里 —— 用户名是登录凭据，邮箱与手机号带唯一索引，
     * 见 {@code UserProfileUpdateCommandDTO} 的说明。
     */
    @PutMapping("/profile")
    public Result<UserLoginResponseDTO.UserDetailResponseDTO> updateProfile(
            @Valid @RequestBody UserProfileUpdateCommandDTO commandDTO) {
        return Result.ok(userService.updateProfile(commandDTO));
    }

    /**
     * 修改自己的密码：必须填写原密码
     *
     * <p>忘记原密码的用户走不到这里，改用「联系管理员」留言（{@code POST /api/feedback/guest}
     * 或登录后的 {@code POST /api/feedback}），由管理员在后台重置。
     */
    @PutMapping("/password")
    public Result<Void> updatePassword(@Valid @RequestBody UserPasswordUpdateCommandDTO commandDTO) {
        userService.updatePassword(commandDTO);
        return Result.ok();
    }

    /**
     * 重置用户密码（管理员）：用户忘记密码时的兜底手段，不需要原密码
     *
     * <p>管理员不能用这条接口改**自己**的密码（服务端会拒绝）：自己的密码必须走
     * {@code PUT /api/user/password} 并填写原密码，否则「改密码要验原密码」这条规则对管理员形同虚设。
     */
    @PutMapping("/admin/{id}/password")
    public Result<UserLoginResponseDTO.UserDetailResponseDTO> resetPassword(
            @PathVariable("id") Long id,
            @Valid @RequestBody UserPasswordResetCommandDTO commandDTO) {

        SecurityUserUtil.requireAdmin();
        return Result.ok(userService.resetPassword(id, commandDTO.getNewPassword()));
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
