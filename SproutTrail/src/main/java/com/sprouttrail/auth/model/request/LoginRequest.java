package com.sprouttrail.auth.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 用户名密码登录请求。 */
@Getter
@Setter
@NoArgsConstructor
public class LoginRequest {

    /** 登录用户名。 */
    @NotBlank(message = "请输入用户名")
    @Size(max = 64, message = "用户名不能超过 64 个字符")
    private String username;

    /** 登录密码；密码不会在日志或响应中回显。 */
    @NotBlank(message = "请输入密码")
    @Size(max = 72, message = "密码不能超过 72 个字符")
    private String password;
}
