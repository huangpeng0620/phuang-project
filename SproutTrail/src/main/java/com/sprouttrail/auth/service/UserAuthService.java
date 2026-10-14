package com.sprouttrail.auth.service;

import com.sprouttrail.auth.model.request.LoginRequest;
import com.sprouttrail.auth.model.request.RegisterRequest;
import com.sprouttrail.auth.model.response.LoginResponse;
import com.sprouttrail.auth.model.response.TokenResponse;

/** 用户注册、登录与会话管理服务。 */
public interface UserAuthService {

    /** 创建启用状态的新用户，并使用 BCrypt 保存密码哈希。 */
    void register(RegisterRequest request);

    /** 校验用户名和密码，并创建 Sa-Token 登录会话。 */
    LoginResponse login(LoginRequest request);

    /** 注销当前请求携带的 token。 */
    void logout();

    /** 获取当前请求所对应的 token 和用户信息。 */
    TokenResponse getCurrentToken();
}
