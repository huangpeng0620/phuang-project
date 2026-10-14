package com.sprouttrail.image.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 新用户注册请求。 */
@Getter
@Setter
@NoArgsConstructor
public class RegisterRequest {

    /** 注册用户名，前后空格会在服务端去除。 */
    @NotBlank(message = "请输入用户名")
    @Size(min = 3, max = 64, message = "用户名长度需为 3～64 个字符")
    private String username;

    /** 注册密码，BCrypt 可处理的最大长度为 72 字节。 */
    @NotBlank(message = "请输入密码")
    @Size(min = 8, max = 72, message = "密码长度需为 8～72 个字符")
    private String password;
}
