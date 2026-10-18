package com.rmh.aispringboot.service;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rmh.aispringboot.DTO.command.ConsultationSessionCreateDTO;
import com.rmh.aispringboot.DTO.response.ConsultationMessageResponseDTO;
import com.rmh.aispringboot.DTO.response.SessionListItemDTO;
import com.rmh.aispringboot.common.ConversationIds;
import com.rmh.aispringboot.entity.ConsultationSession;
import com.rmh.aispringboot.entity.User;
import com.rmh.aispringboot.exception.BusinessException;
import com.rmh.aispringboot.mapper.ConsultationSessionMapper;
import com.rmh.aispringboot.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ConsultationSessionService {

    /** 业务会话 ID 前缀：前端可能传 "session_1"，也可能传裸 id "1" */
    private static final String SESSION_ID_PREFIX = "session_";

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private ConsultationSessionMapper consultationSessionMapper;
    @Autowired
    private ConsultationMessageService consultationMessageService;

    @Autowired
    private ChatMemory chatMemory;

    public ConsultationSession createSession(Long userId, ConsultationSessionCreateDTO createDTO)
    {
        User user = userMapper.selectById(userId);
        //先验证用户是否存在
        if(user!=null)
        {
            //创建会话记录
            ConsultationSession session = ConsultationSession.builder()
                    .userId(userId)
                    .sessionTitle(createDTO.getSessionTitle())
                    .startedAt(LocalDateTime.now())
                    .build();

            if(StrUtil.isBlank(createDTO.getSessionTitle())){
                session.setSessionTitle("心理健康AI助手" + DateUtil.format(LocalDateTime.now(), "MM-dd HH-mm"));
            }
            //插入
            consultationSessionMapper.insert(session);
            return session;
        }
        return null;
    }

    /**
     * 按主键查询会话（不校验归属）
     *
     * <p>仅供**已经做过权限判断**的调用方使用（例如管理员查看任意会话的消息）；
     * 普通用户路径一律用 {@link #getSessionByIdAndUserId(Long, Long)}。
     */
    public ConsultationSession getSessionById(Long sessionId) {
        return sessionId == null ? null : consultationSessionMapper.selectById(sessionId);
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

    /**
     * 分页查询会话列表
     *
     * @param userId  当前登录用户 ID
     * @param isAdmin 是否为管理员：管理员可见全部会话（并附带用户昵称），普通用户仅可见自己的
     */
    public Page<SessionListItemDTO> listSessions(Long userId, boolean isAdmin, int page, int size) {
        LambdaQueryWrapper<ConsultationSession> queryWrapper = new LambdaQueryWrapper<>();
        if (!isAdmin) {
            queryWrapper.eq(ConsultationSession::getUserId, userId);
        }
        queryWrapper.orderByDesc(ConsultationSession::getStartedAt);

        Page<ConsultationSession> sessionPage =
                consultationSessionMapper.selectPage(new Page<>(page, size), queryWrapper);

        List<SessionListItemDTO> records = sessionPage.getRecords().stream()
                .map(session -> toListItem(session, isAdmin))
                .collect(Collectors.toList());

        Page<SessionListItemDTO> result =
                new Page<>(sessionPage.getCurrent(), sessionPage.getSize(), sessionPage.getTotal());
        result.setRecords(records);
        return result;
    }

    public void deleteSession(Long sessionId, Long userId) {
        if (getSessionByIdAndUserId(sessionId, userId) == null) {
            throw new BusinessException("会话不存在或无权访问");
        }
        consultationMessageService.deleteBySessionId(sessionId);
        consultationSessionMapper.deleteById(sessionId);
        // 记忆窗口不在库里，删会话要顺手清掉，否则 Redis 里会留下再也访问不到的 key
        try {
            chatMemory.clear(ConversationIds.ofSessionId(sessionId));
        } catch (Exception e) {
            log.warn("清空对话记忆失败 sessionId={}：{}", sessionId, e.getMessage());
        }
    }

    /**
     * 保存会话的情绪分析结果（JSON 字符串），同时刷新分析时间
     *
     * <p>用 {@code updateById} 会忽略 null 字段，这里显式指定要更新的两列。
     */
    public void saveEmotionAnalysis(Long sessionId, String analysisJson) {
        if (sessionId == null) {
            return;
        }
        consultationSessionMapper.update(null,
                Wrappers.<ConsultationSession>lambdaUpdate()
                        .eq(ConsultationSession::getId, sessionId)
                        .set(ConsultationSession::getLastEmotionAnalysis, analysisJson)
                        .set(ConsultationSession::getLastEmotionUpdatedAt, LocalDateTime.now()));
    }

    /**
     * 宽容解析会话 ID：既接受 "session_1" 也接受裸 "1"（列表接口返回的是裸 id），
     * 非法格式返回 null。
     */
    public Long parseSessionId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.startsWith(SESSION_ID_PREFIX)
                ? raw.substring(SESSION_ID_PREFIX.length())
                : raw;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 组装列表项：补上「最后一条消息」「消息条数」「时长」等前端展示所需字段
     */
    private SessionListItemDTO toListItem(ConsultationSession session, boolean withUserInfo) {
        ConsultationMessageResponseDTO lastMessage =
                consultationMessageService.getLastMessageBySessionId(session.getId());
        Integer messageCount = consultationMessageService.getMessageCountBySessionId(session.getId());

        LocalDateTime lastMessageTime =
                lastMessage != null && lastMessage.getCreatedAt() != null
                        ? lastMessage.getCreatedAt()
                        : session.getStartedAt();

        Long durationMinutes = null;
        if (session.getStartedAt() != null && lastMessageTime != null) {
            durationMinutes = Duration.between(session.getStartedAt(), lastMessageTime).toMinutes();
        }

        SessionListItemDTO.SessionListItemDTOBuilder builder = SessionListItemDTO.builder()
                .id(session.getId())
                .sessionTitle(session.getSessionTitle())
                .startedAt(session.getStartedAt())
                .lastMessageContent(lastMessage != null ? lastMessage.getContent() : null)
                .messageCount(messageCount)
                .durationMinutes(durationMinutes)
                .lastMessageTime(lastMessageTime);

        if (withUserInfo) {
            User user = userMapper.selectById(session.getUserId());
            builder.userNickname(user != null ? user.getNickname() : null);
        }
        return builder.build();
    }
}
