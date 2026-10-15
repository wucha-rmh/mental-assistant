package com.rmh.aispringboot.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rmh.aispringboot.DTO.command.UserLoginCommandDTO;
import com.rmh.aispringboot.DTO.command.UserRegisterCommandDTO;
import com.rmh.aispringboot.DTO.response.FileUploadResponseDTO;
import com.rmh.aispringboot.DTO.response.UserLoginResponseDTO;
import com.rmh.aispringboot.entity.User;
import com.rmh.aispringboot.enumClass.UserType;
import com.rmh.aispringboot.exception.BusinessException;
import com.rmh.aispringboot.mapper.UserMapper;
import com.rmh.aispringboot.service.convert.UserConvert;
import com.rmh.aispringboot.util.JwtTokenUtil;
import com.rmh.aispringboot.util.SecurityUserUtil;
import jakarta.annotation.Resource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

@Service
public class UserService {

     private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Resource
    private UserMapper userMapper;

    @Resource
    private FileStorageService fileStorageService;

    public UserLoginResponseDTO login(UserLoginCommandDTO commandDTO)
    {
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUsername ,commandDTO.getUsername())
                .or()
                .eq(User::getPassword,commandDTO.getPassword());

        User user=userMapper.selectOne(queryWrapper);
        System.out.println(user);
        if(user==null)
        {
            throw new BusinessException("用户不存在");
        }

        String inputPassword=commandDTO.getPassword();
        if(!passwordEncoder.matches(inputPassword,user.getPassword()))
        {
            throw new BusinessException("密码错误");
        }

        if(!user.isActive())
        {
            throw new BusinessException("用户被禁用，请联系管理员");
        }
        String token= JwtTokenUtil.generateToken(user.getId(),user.getUsername(), user.getUserType());
        UserLoginResponseDTO.UserDetailResponseDTO userDetailResponseDTO = UserConvert.entityToDetaiResponse(user);
        return UserConvert.entityToResponseDTO(token,userDetailResponseDTO);
    }

    public  UserLoginResponseDTO.UserDetailResponseDTO register(UserRegisterCommandDTO commandDTO) {

        System.out.println(JSONUtil.parseObj(commandDTO));
        //验证密码是否一致
        if (!commandDTO.getPassword().equals(commandDTO.getConfirmPassword())) {
            throw new BusinessException("两次输入密码不一致");
        }
        //检查用户名是否重复
        LambdaQueryWrapper<User> userNameQuery = new LambdaQueryWrapper<>();
        userNameQuery.eq(User::getUsername, commandDTO.getUsername());
        if (userMapper.selectCount(userNameQuery) > 0) {
            throw new BusinessException("用户名已存在");
        }
        //检查用户邮箱是否已存在
        LambdaQueryWrapper<User> emailQuery = new LambdaQueryWrapper<>();
        emailQuery.eq(User::getEmail, commandDTO.getEmail());
        if (userMapper.selectCount(emailQuery) > 0) {
            throw new BusinessException("邮箱已经存在");
        }
        //判断用户类型是否存在
        if (!UserType.isValidCode(commandDTO.getUserType())) {
            throw new BusinessException("用户类型不存在");
        }
        //创建用户
        String password = commandDTO.getPassword().trim();
        String encodedPassword = passwordEncoder.encode(password);
        User user = UserConvert.registerCommandToEntity(commandDTO,encodedPassword);

        //插入数据库
        userMapper.insert(user);

        return UserConvert.entityToDetaiResponse(user);

    }
    public UserLoginResponseDTO.UserDetailResponseDTO getUserById(Long userid) {
        User user=userMapper.selectById(userid);
        if(user==null)
        {
            throw new BusinessException("用户不存在");
        }
        return UserConvert.entityToDetaiResponse(user);
    }

    /**
     * 上传/更换当前登录用户的头像。
     *
     * <p>文件落在项目内（{@code backend/uploads/bussiness/user_avatar/}），
     * {@code user.avatar} 存相对路径 {@code /files/...}，前端拼 {@code fileBaseUrl} 展示；
     * 换头像时顺手清掉上一个头像文件，避免 uploads 目录里堆孤儿文件。
     *
     * @param file 前端以 {@code multipart/form-data} 的 {@code file} 字段提交的图片
     * @return 更新后的用户信息（前端直接覆盖本地缓存的 userInfo）
     */
    public UserLoginResponseDTO.UserDetailResponseDTO updateAvatar(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要上传的头像图片");
        }

        Long userId = SecurityUserUtil.currentUser().getId();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        // 先落盘 + 记 sys_file_info
        FileUploadResponseDTO stored = fileStorageService.store(
                file, FileStorageService.BUSINESS_TYPE_AVATAR, String.valueOf(userId), "avatar");
        // 头像只能是图片，否则删掉刚写入的文件并报错
        if (!FileStorageService.FILE_TYPE_IMAGE.equals(stored.getFileType())) {
            fileStorageService.deleteStoredFile(stored.getFilePath());
            throw new BusinessException("头像只能是图片文件（jpg / png / gif / webp / svg）");
        }

        String oldAvatar = user.getAvatar();
        user.setAvatar(stored.getFilePath());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);

        // 清理上一个头像文件（只清理头像目录下的，且不是同一个文件）
        if (oldAvatar != null && oldAvatar.startsWith(FileStorageService.AVATAR_URL_PREFIX)
                && !oldAvatar.equals(stored.getFilePath())) {
            fileStorageService.deleteStoredFile(oldAvatar);
        }

        return UserConvert.entityToDetaiResponse(user);
    }
}
