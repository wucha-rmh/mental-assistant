package com.rmh.aispringboot.DTO.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理员回复留言
 *
 * <p>回复后留言状态自动变为「已回复」（1），用户在「我的留言」里能看到这条回复。
 */
@Data
public class UserFeedbackReplyCommandDTO {

    @NotBlank(message = "回复内容不能为空")
    @Size(max = 1000, message = "回复内容最多1000个字符")
    private String reply;
}
