package com.rmh.aispringboot.controller;


import com.rmh.aispringboot.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class Test {


    @GetMapping("/test")
    public Result<String> test(){
        Result result=new Result();
//        result.setCode("2000");
//        System.out.println(result.getCode());
        return result.ok("hello world");

    }
}
