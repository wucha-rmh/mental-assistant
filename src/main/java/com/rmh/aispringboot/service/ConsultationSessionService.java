package com.rmh.aispringboot.service;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
                session.setSessionTitle("心理健康AI助手" + DateUtil.format(LocalDateTime.now(), "MM-dd HH-mm"));
            }
            //插入
            consultationSessionMapper.insert(session);
            return session;
        }
        //先验证用户是否存在
        return null;
    }

    /**
     * 按「会话 ID + 用户 ID」查询会话，用于校验会话归属（防止越权访问他人会话）
     *
     * <p>用 ID + userId 双条件查询，而非先查再比对：查不到与不属于当前用户
     * 返回同样的 null，调用方无法据此判断他人会话是否存在。
     */
    public ConsultationSession getSessionByIdAndUserId(Long sessionId, Long userId) {
        if (sessionId == null || userId == null) {
            return null;
        }
        LambdaQueryWrapper<ConsultationSession> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ConsultationSession::getId, sessionId)
                .eq(ConsultationSession::getUserId, userId);
        return consultationSessionMapper.selectOne(queryWrapper);
    }
}
