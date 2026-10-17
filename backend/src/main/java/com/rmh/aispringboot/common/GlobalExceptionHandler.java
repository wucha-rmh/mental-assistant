package com.rmh.aispringboot.common;


import com.rmh.aispringboot.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    //处理参数校验异常
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<String> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {

        String message=e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(","));
        String msg = (message == null || message.isBlank())
                ? ResultCode.PARAM_ERROR.getMsg()
                : message;
        return Result.error(ResultCode.PARAM_ERROR.getCode(), msg, null);
    }
    //处理业务异常
    @ExceptionHandler(BusinessException.class)
    public Result<?> handleBusinessException(BusinessException e) {
        if(e.getData()!=null){
            return Result.error(e.getCode(),e.getMessage(),e.getData());
        }
        else return Result.error(e.getCode(),e.getMessage(),null);
    }

    //处理上传文件过大（spring.servlet.multipart.max-file-size，见 application.yaml）
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<String> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        log.warn("上传文件超过大小限制: {}", e.getMessage());
        return Result.error(ResultCode.FILE_SIZE_EXCEEDED.getCode(),
                "上传的文件过大，单个文件不能超过 5MB", null);
    }

    /**
     * 静态资源不存在：/files/** 取不到文件、或访问到未映射的路径时，
     * Spring MVC 抛 NoResourceFoundException。不单独处理就会被下面的 Exception 兜底
     * 转成 HTTP 200 + code 500 —— 语义是错的：浏览器、日志、监控都会把 404 当成功，
     * 真实错误率看不出来；这里改成规范的 404（响应体仍是统一响应体，前端照样能读到 msg）。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<String> handleNoResourceFoundException(NoResourceFoundException e) {
        log.warn("资源不存在: {}", e.getResourcePath());
        return Result.error(ResultCode.FILE_NOT_FOUND.getCode(), "文件不存在", null);
    }

    /**
     * 兜底：未被上面几个处理器覆盖的异常（JSON 解析失败、NPE、SQL 异常等）
     * 也返回统一响应体，避免前端拿到 Spring 默认错误 JSON 而读不到 msg。
     */
    @ExceptionHandler(Exception.class)
    public Result<String> handleException(Exception e) {
        log.error("未处理的异常", e);
        return Result.error(ResultCode.SYSTEM_ERROR.getCode(), "服务器开小差了，请稍后再试", null);
    }
}
