package com.rmh.aispringboot.controller;


import cn.hutool.json.JSONUtil;
import com.rmh.aispringboot.AiService.PsychologicalSupportService;
import com.rmh.aispringboot.AiService.StructOutPut;
import com.rmh.aispringboot.DTO.command.ConsultationSessionCreateDTO;
import com.rmh.aispringboot.DTO.command.ConsultationstreamDTO;
import com.rmh.aispringboot.common.Result;
import com.rmh.aispringboot.common.ResultCode;
import com.rmh.aispringboot.util.JwtTokenUtil;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.awt.*;
import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/psychological-chat")
public class PsychologicalChat{
    @Autowired
    private PsychologicalSupportService psychologicalSupportService;
    @PostMapping("/session/start")
    public Result<StructOutPut.StreamChatSession> startSession(@Valid @RequestBody ConsultationSessionCreateDTO createDTO){
        //获取当前用户
//        String currentToken = JwtTokenUtil.getCurrentToken();
//        DecodedJWT decodedJWT = JwtTokenUtil.verifyToken(currentToken);
//        Long userId = decodedJWT.getClaim("userId").asLong();
        Long userId=JwtTokenUtil.getCurrentUserId();
        StructOutPut.StreamChatSession streamChatSession = psychologicalSupportService.startSession(userId, createDTO);

        return Result.ok(streamChatSession);
    }
    @PostMapping(value = "/stream",produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamChat(@Valid @RequestBody ConsultationstreamDTO streamDTO)
    {
//        //获取当前用户
//        String currentToken = JwtTokenUtil.getCurrentToken();
//        DecodedJWT decodedJWT = JwtTokenUtil.verifyToken(currentToken);
//        Long userId = decodedJWT.getClaim("userId").asLong();
        Long userId=JwtTokenUtil.getCurrentUserId();
        if(userId==null)
        {
            return Flux.just(ServerSentEvent.<String>builder()
                    .event("error")
                    .data(JSONUtil.toJsonStr(Result.error(ResultCode.UNAUTHORIZED.getCode(),ResultCode.UNAUTHORIZED.getMsg(),"用户未登录")))
            .build());

        }


        //开始流式对话

        return psychologicalSupportService.streamPsychologicalChat(streamDTO.getSessionId(),streamDTO.getUserMessage())
                .map(Fragment->{
                    return ServerSentEvent.<String>builder()
                            .event("message")
                            .data(JSONUtil.toJsonStr(Result.ok(Map.of("content",Fragment,"Type","normal"))))
                            .build();
                })
                .concatWith(Flux.just(ServerSentEvent.<String>builder()
                                .event("done")
                                .data("{}")
                        .build()))
                .delayElements(Duration.ofMillis(10));//添加延时，确保流式输出体验

    }
}
