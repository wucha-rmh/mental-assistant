package com.rmh.aispringboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rmh.aispringboot.DTO.response.ConsultationMessageResponseDTO;
import com.rmh.aispringboot.entity.ConsultationMessage;
import com.rmh.aispringboot.mapper.ConsultationMessageMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ConsultationMessageService {
    @Autowired
    private ConsultationMessageMapper consultationMessageMapper;
    public ConsultationMessage saveUserMessage(Long sessionId,String content,String emotion_tag){
        //构建用户消息实体
        ConsultationMessage userMeaasge = ConsultationMessage.builder()
                .sessionId(sessionId)
                .senderType(1)
                .messageType(1)
                .content(content)
                .emotionTag(emotion_tag)
                .createdAt(LocalDateTime.now())
                .build();
        consultationMessageMapper.insert(userMeaasge);
        return userMeaasge;
    }
    public ConsultationMessage saveAiMessage(Long sessionId,String content,String aiModel){

        ConsultationMessage message = ConsultationMessage.builder()
                .sessionId(sessionId)
                .senderType(2)
                .messageType(1)
                .content(content)
                .aiModel(aiModel)
                .createdAt(LocalDateTime.now())
                .build();
        consultationMessageMapper.insert(message);
        return message;
    }

    public Integer getMessageCountBySessionId(Long sessionId){
        LambdaQueryWrapper<ConsultationMessage> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ConsultationMessage::getSessionId,sessionId);
        Long count = consultationMessageMapper.selectCount(queryWrapper);
        return count.intValue();
    }
    //获取会话最后一条消息
    public ConsultationMessageResponseDTO getLastMessageBySessionId(Long sessionId){
        LambdaQueryWrapper<ConsultationMessage> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ConsultationMessage::getSessionId,sessionId)
                .orderByDesc(ConsultationMessage::getCreatedAt)
                .last("limit 1");
        ConsultationMessage lastMessage = consultationMessageMapper.selectOne(queryWrapper);
        return lastMessage!=null?convertToResponseDTO(lastMessage):null;
    }

    /**
     * 查询某会话的全部消息，按时间升序（即对话的自然顺序）
     */
    public List<ConsultationMessageResponseDTO> listMessagesBySessionId(Long sessionId){
        LambdaQueryWrapper<ConsultationMessage> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ConsultationMessage::getSessionId, sessionId)
                .orderByAsc(ConsultationMessage::getCreatedAt);
        return consultationMessageMapper.selectList(queryWrapper).stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }

    /**
     * 删除某会话的全部消息，返回删除条数（删除会话时级联清理）
     */
    public int deleteBySessionId(Long sessionId){
        LambdaQueryWrapper<ConsultationMessage> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ConsultationMessage::getSessionId, sessionId);
        return consultationMessageMapper.delete(queryWrapper);
    }

    private ConsultationMessageResponseDTO convertToResponseDTO(ConsultationMessage message) {
        if (message == null) {
            return null;
        }

        // 手动逐字段赋值，确保转换的准确性和可控性
        ConsultationMessageResponseDTO responseDTO = new ConsultationMessageResponseDTO();
        responseDTO.setId(message.getId());
        responseDTO.setSessionId(message.getSessionId());
        responseDTO.setSenderType(message.getSenderType());
        responseDTO.setMessageType(message.getMessageType());
        responseDTO.setContent(message.getContent());
        responseDTO.setEmotionTag(message.getEmotionTag());
        responseDTO.setAiModel(message.getAiModel());
        responseDTO.setCreatedAt(message.getCreatedAt());

        // 设置描述字段（通过实体方法获取）
        responseDTO.setSenderTypeDesc(message.getSenderTypeDesc());
        responseDTO.setMessageTypeDesc(message.getMessageTypeDesc());

        // 计算消息长度
        responseDTO.calculateContentLength();

        return responseDTO;
    }
}
