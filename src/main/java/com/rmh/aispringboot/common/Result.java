package com.rmh.aispringboot.common;

import lombok.Data;

@Data
public class Result<T>{
    private String code;
    private String msg;
    private T data;
    public static <T> Result<T> ok()
    {
        Result<T> result=new Result<>();
        result.setCode(Resultcode.SUCCESS.getCode());
        result.setMsg(Resultcode.SUCCESS.getMsg());
        return result;
    }

    public static <T> Result<T> ok(T data)
    {
        Result<T> result=ok();
        result.setData(data);
        return result;
    }

    public static <T> Result<T> error()
    {
        Result<T> result=new Result<>() ;
        result.setCode(Resultcode.ERROR.getCode());
        result.setMsg(Resultcode.ERROR.getMsg());
        return result;
    }

    public static <T> Result<T> error(String code,String msg,T data)
    {
        Result<T> result=new Result<>();
        result.setCode(code);
        result.setMsg(msg);
        result.setData(data);
        return result;
    }


}
