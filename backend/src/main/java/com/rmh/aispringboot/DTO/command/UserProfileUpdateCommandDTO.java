package com.rmh.aispringboot.DTO.command;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 修改个人资料的请求体（登录用户改自己）
 *
 * <p>只开放昵称 / 性别 / 生日三项：
 * <ul>
 *   <li>用户名是登录凭据，不改；</li>
 *   <li>邮箱、手机号在库里是唯一索引，改动要先查重（还要考虑找回密码的凭据），不在本轮范围内；</li>
 *   <li>用户类型、状态只有管理员能改（各自有专用接口）。</li>
 * </ul>
 *
 * <p>字段留空表示「清空该项」——服务端用 {@code LambdaUpdateWrapper} 显式 set，
 * 而不是 {@code updateById}（后者忽略 null，改完清不掉，性别会永远停在旧值）。
 */
@Data
public class UserProfileUpdateCommandDTO {

    /** 昵称，空串或空白表示清空（显示名会回退成用户名） */
    @Size(max = 50, message = "昵称长度不能超过50个字符")
    private String nickname;

    /** 性别 0:未知 1:男 2:女；null 表示清空 */
    @Min(value = 0, message = "性别取值不正确")
    @Max(value = 2, message = "性别取值不正确")
    private Integer gender;

    /** 生日 yyyy-MM-dd；null 表示清空 */
    @PastOrPresent(message = "生日不能晚于今天")
    private LocalDate birthday;
}
