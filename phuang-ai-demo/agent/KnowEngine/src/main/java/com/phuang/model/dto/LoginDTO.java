package com.phuang.model.dto;

import lombok.Data;

/**
 * 登录请求参数
 */
@Data
public class LoginDTO {

    /**
     * 手机号（登录账号）
     */
    private String phone;

    /**
     * 密码
     */
    private String password;
}
