package com.rmh.aispringboot.AiService;


import com.rmh.aispringboot.DTO.command.ConsultationSessionCreateDTO;
import com.rmh.aispringboot.DTO.response.ConsultationMessageResponseDTO;
import com.rmh.aispringboot.entity.ConsultationSession;
import com.rmh.aispringboot.service.ConsultationMessageService;
import com.rmh.aispringboot.service.ConsultationSessionService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

@Service
public class PsychologicalSupportService {
    @Autowired
    @Qualifier("open-ai")
    private ChatClient chatClient;
    @Autowired
    private ChatMemory chatMemory;
    @Autowired
    private ConsultationSessionService consultationSessionService;
    @Autowired
    private ConsultationMessageService consultationMessageService;

    //开始新会话
    public StructOutPut.StreamChatSession startSession(Long userId,ConsultationSessionCreateDTO createDTO) {
        //创建数据库会话记录
        ConsultationSession session = consultationSessionService.createSession(userId, createDTO);

        //将初始用户消息保存到Message表
        consultationMessageService.saveUserMessage(session.getId(),createDTO.getInitialMessage(),null);

        //创建会话信息
        String sessionId="session_"+session.getId();
        return new StructOutPut.StreamChatSession(
                sessionId,
                userId,
                createDTO.getInitialMessage(),
                System.currentTimeMillis(),
                System.currentTimeMillis()+86400000L,//24小时
                1,
                "ACTIVE"
        );
    }

    public Flux<String> streamPsychologicalChat(String sessionIdString,String userMessage)
    {
        //创建响应流
        return  Flux.create(sink->{
            //sink.next() 发布数据流4
            //sink.complete 完成数据流
            //sink.error(exxception) 发布错误
            Long dbSessionId = extractSessionId(sessionIdString);
            if(dbSessionId==null)
            {
                sink.error(new RuntimeException("会话ID格式错误"));
                return;
            }
            //是否为初始消息
            boolean isInitialMessage=false;
            //判断是否为初始消息，避免重复保存
            Integer messageCount = consultationMessageService.getMessageCountBySessionId(dbSessionId);
            if(messageCount==1)
            {
                ConsultationMessageResponseDTO lastMessage = consultationMessageService.getLastMessageBySessionId(dbSessionId);
                if(lastMessage!=null&&lastMessage.getSenderType()==1&&userMessage.equals(lastMessage.getContent()))
                {
                    isInitialMessage=true;
                }
            }
            if(!isInitialMessage)
            {
                //保存用户消息到数据库
                consultationMessageService.saveUserMessage(dbSessionId,userMessage,null);
            }
            //进行流式对话
            //生成对话记忆管理
            String conversationId="conversation_"+sessionIdString;
            //构建系统提示词
            List<Message> userMessages=new ArrayList<>();
            userMessages.add(new UserMessage(userMessage));
            chatMemory.add(conversationId,userMessages);
            Prompt prompt = new Prompt(List.of(
                    new SystemMessage(PromptManage.PSYCHOLOGICAL_SUPPORT_SYSTEM_PROMPT)
            ));
            //用于存储AI完整的响应
            StringBuilder fullResponse=new StringBuilder();
            //使用chatClient发送消息到open-ai
            chatClient.prompt(prompt)
                    .user(userMessage)
                    .advisors(advisorSpec -> advisorSpec.param(chatMemory.CONVERSATION_ID,conversationId))
                    .stream()
                    .content()
                    .doOnNext(Fragment->{
                        fullResponse.append(Fragment);
                        sink.next(Fragment);
                    })
                    .doOnComplete(()->{
                        //将AI返回的内容保存到数据库里
                        String completeRes = fullResponse.toString();
                        consultationMessageService.saveAiMessage(dbSessionId,completeRes,"openai");
                        //添加AI回复到chatMemory
                        List<Message> aiMessages = new ArrayList<>();
                        aiMessages.add(new AssistantMessage(completeRes));
                        chatMemory.add(conversationId,aiMessages);
                        sink.complete();
                    })
                    .doOnError(error->{
                        sink.error(error);
                    })
                    .subscribe(); //订阅并启动流
        });
    }

    //获取参数中的sessionId
    public Long extractSessionId(String sessionId)
    {
        if(sessionId!=null&&sessionId.startsWith("session_"))
        {
            return Long.parseLong(sessionId.substring(8));
        }
        return null;
    }

}
