package com.sprouttrail.image.service;

import com.sprouttrail.image.model.GeneratedImage;
import com.sprouttrail.image.model.ImageAspectRatio;
import com.sprouttrail.image.model.ImageGenerationRecord;

/** 用户登录与会话管理服务。 */
public interface UserAuthService {

    /** 校验用户名和密码，并创建 Sa-Token 登录会话。 */
    ImageGenerationRecord.LoginResponse login(GeneratedImage.LoginRequest request);

    /** 注销当前请求携带的 token。 */
    void logout();

    /** 获取当前请求所对应的 token 和用户信息。 */
    ImageAspectRatio.TokenResponse getCurrentToken();
}
