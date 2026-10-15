package com.rmh.aispringboot.controller;


import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rmh.aispringboot.AiService.EmotionAnalysisService;
import com.rmh.aispringboot.AiService.PsychologicalSupportService;
import com.rmh.aispringboot.AiService.StructOutPut;
import com.rmh.aispringboot.DTO.command.ConsultationSessionCreateDTO;
import com.rmh.aispringboot.DTO.command.ConsultationstreamDTO;
import com.rmh.aispringboot.DTO.response.ConsultationMessageResponseDTO;
import com.rmh.aispringboot.DTO.response.SessionEmotionDTO;
import com.rmh.aispringboot.DTO.response.SessionListItemDTO;
import com.rmh.aispringboot.DTO.response.UserLoginResponseDTO;
import com.rmh.aispringboot.common.Result;
import com.rmh.aispringboot.common.ResultCode;
import com.rmh.aispringboot.exception.BusinessException;
import com.rmh.aispringboot.service.ConsultationMessageService;
import com.rmh.aispringboot.service.ConsultationSessionService;
import com.rmh.aispringboot.util.JwtTokenUtil;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/psychological-chat")
public class PsychologicalChat {

    /** 管理员用户类型 */
    private static final int USER_TYPE_ADMIN = 2;

    @Autowired
    private PsychologicalSupportService psychologicalSupportService;
    @Autowired
    private ConsultationSessionService consultationSessionService;
    @Autowired
    private ConsultationMessageService consultationMessageService;
    @Autowired
    private EmotionAnalysisService emotionAnalysisService;

    @PostMapping("/session/start")
    public Result<StructOutPut.StreamChatSession> startSession(@Valid @RequestBody ConsultationSessionCreateDTO createDTO){
        Long userId=JwtTokenUtil.getCurrentUserId();
        StructOutPut.StreamChatSession streamChatSession = psychologicalSupportService.startSession(userId, createDTO);

        return Result.ok(streamChatSession);
    }

    @PostMapping(value = "/stream",produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamChat(@Valid @RequestBody ConsultationstreamDTO streamDTO)
    {
        Long userId=JwtTokenUtil.getCurrentUserId();
        if(userId==null)
        {
            return Flux.just(ServerSentEvent.<String>builder()
                    .event("error")
                    .data(JSONUtil.toJsonStr(Result.error(ResultCode.UNAUTHORIZED.getCode(),ResultCode.UNAUTHORIZED.getMsg(),"用户未登录")))
            .build());

        }
        //开始流式对话

        return psychologicalSupportService.streamPsychologicalChat(userId, streamDTO.getSessionId(),streamDTO.getUserMessage())
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
                //会话不存在/无权访问等异常，同样以 SSE 事件返回，避免前端只看到连接中断
                .onErrorResume(e -> Flux.just(ServerSentEvent.<String>builder()
                        .event("error")
                        .data(JSONUtil.toJsonStr(Result.error(ResultCode.ERROR.getCode(), e.getMessage(), null)))
                        .build()))
                .delayElements(Duration.ofMillis(10));//添加延时，确保流式输出体验
    }

    /**
     * 会话列表（分页）
     *
     * <p>普通用户只能看到自己的会话；管理员可以看到全部会话并附带用户昵称。
     * <p>分页参数兼容两套命名：用户端用 {@code pageNum/pageSize}，管理后台用 {@code currentPage/size}。
     */
    @GetMapping("/sessions")
    public Result<Page<SessionListItemDTO>> listSessions(
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) Integer currentPage,
            @RequestParam(required = false) Integer size) {

        UserLoginResponseDTO.UserDetailResponseDTO user = currentUser();

        int page = pageNum != null ? pageNum : (currentPage != null ? currentPage : 1);
        int limit = pageSize != null ? pageSize : (size != null ? size : 10);
        boolean isAdmin = user.getUserType() != null && user.getUserType() == USER_TYPE_ADMIN;

        return Result.ok(consultationSessionService.listSessions(user.getId(), isAdmin, page, limit));
    }

    /**
     * 查询某会话的完整消息记录（按时间升序）
     */
    @GetMapping("/sessions/{sessionId}/messages")
    public Result<List<ConsultationMessageResponseDTO>> listMessages(@PathVariable String sessionId) {
        UserLoginResponseDTO.UserDetailResponseDTO user = currentUser();
        Long dbSessionId = requireOwnedSessionId(sessionId, user.getId());
        return Result.ok(consultationMessageService.listMessagesBySessionId(dbSessionId));
    }

    /**
     * 删除会话（连同其全部消息）
     */
    @DeleteMapping("/sessions/{sessionId}")
    public Result<Void> deleteSession(@PathVariable String sessionId) {
        UserLoginResponseDTO.UserDetailResponseDTO user = currentUser();
        Long dbSessionId = requireOwnedSessionId(sessionId, user.getId());
        consultationSessionService.deleteSession(dbSessionId, user.getId());
        return Result.ok();
    }

    /**
     * 会话情绪分析
     *
     * <p>优先复用会话上缓存的分析结果；缓存早于会话最新消息时调用 AI 重新分析并落库；
     * 没有任何消息或 AI 调用失败时返回默认值（中性），保证前端情绪面板不报错。
     */
    @GetMapping("/session/{sessionId}/emotion")
    public Result<SessionEmotionDTO> getSessionEmotion(@PathVariable String sessionId) {
        UserLoginResponseDTO.UserDetailResponseDTO user = currentUser();
        Long dbSessionId = requireOwnedSessionId(sessionId, user.getId());
        return Result.ok(emotionAnalysisService.analyzeOrGet(dbSessionId, user.getId()));
    }

    // ── 内部工具 ────────────────────────────────────────────────────

    /**
     * 取当前登录用户：principal 由 JwtAuthenticationFilter 写入
     */
    private UserLoginResponseDTO.UserDetailResponseDTO currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !(authentication.getPrincipal() instanceof UserLoginResponseDTO.UserDetailResponseDTO user)) {
            throw new BusinessException("用户未登录");
        }
        return user;
    }

    /**
     * 解析会话 ID 并校验归属，返回数据库主键；会话不存在或不属于当前用户时抛业务异常
     */
    private Long requireOwnedSessionId(String rawSessionId, Long userId) {
        Long dbSessionId = consultationSessionService.parseSessionId(rawSessionId);
        if (dbSessionId == null) {
            throw new BusinessException("会话ID格式错误");
        }
        if (consultationSessionService.getSessionByIdAndUserId(dbSessionId, userId) == null) {
            throw new BusinessException("会话不存在或无权访问");
        }
        return dbSessionId;
    }
}
