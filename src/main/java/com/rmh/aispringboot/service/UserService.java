package com.rmh.aispringboot.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rmh.aispringboot.DTO.command.UserLoginCommandDTO;
import com.rmh.aispringboot.DTO.command.UserRegisterCommandDTO;
import com.rmh.aispringboot.DTO.response.UserLoginResponseDTO;
import com.rmh.aispringboot.common.Result;
import com.rmh.aispringboot.entity.User;
import com.rmh.aispringboot.enumClass.UserType;
import com.rmh.aispringboot.exception.BusinessException;
import com.rmh.aispringboot.mapper.UserMapper;
import com.rmh.aispringboot.service.convert.UserConvert;
import com.rmh.aispringboot.util.JwtTokenUtil;
import jakarta.annotation.Resource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

     private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Resource
    private UserMapper userMapper;

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
        User user = UserConvert.registerCommadnToEntity(commandDTO,encodedPassword);

        //插入数据库
        userMapper.insert(user);

        return UserConvert.entityToDetaiResponse(user);

    }
}
