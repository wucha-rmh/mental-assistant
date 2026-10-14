package com.rmh.aispringboot.service;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.rmh.aispringboot.DTO.command.ConsultationSessionCreateDTO;
import com.rmh.aispringboot.entity.ConsultationSession;
import com.rmh.aispringboot.entity.User;
import com.rmh.aispringboot.mapper.ConsultationSessionMapper;
import com.rmh.aispringboot.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class ConsultationSessionService {
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private ConsultationSessionMapper consultationSessionMapper;
    public ConsultationSession createSession(Long userId, ConsultationSessionCreateDTO createDTO)
    {
        User user = userMapper.selectById(userId);
        if(user!=null)
        {
            //创建会话记录
            ConsultationSession session = ConsultationSession.builder()
                    .userId(userId)
                    .sessionTitle(createDTO.getSessiontitle())
                    .startedAt(LocalDateTime.now())
                    .build();

            if(StrUtil.isBlank(createDTO.getSessiontitle())){
                session.setSessionTitle(String.format("心理健康AI助手"+ DateUtil.format(LocalDateTime.now(), "MM-dd HH-mm")));
            }
            //插入
            consultationSessionMapper.insert(session);
            return session;
        }
        //先验证用户是否存在



        return null;
    }
}
