package com.rmh.aispringboot.common;


import com.rmh.aispringboot.exception.BusinessException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

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
}
