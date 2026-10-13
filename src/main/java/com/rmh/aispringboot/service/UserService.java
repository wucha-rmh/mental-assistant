package com.rmh.aispringboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rmh.aispringboot.DTO.command.UserLoginCommandDTO;
import com.rmh.aispringboot.DTO.response.UserLoginResponseDTO;
import com.rmh.aispringboot.common.Result;
import com.rmh.aispringboot.entity.User;
import com.rmh.aispringboot.exception.BusinessException;
import com.rmh.aispringboot.mapper.UserMapper;
import jakarta.annotation.Resource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

     private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Resource
    private UserMapper userMapper;

    public Result<UserLoginResponseDTO> login(UserLoginCommandDTO commandDTO)
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
        return null;

    }
}
