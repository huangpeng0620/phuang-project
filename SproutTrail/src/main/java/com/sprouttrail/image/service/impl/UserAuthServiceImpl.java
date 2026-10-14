package com.sprouttrail.image.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sprouttrail.image.model.GeneratedImage;
import com.sprouttrail.image.model.ImageAspectRatio;
import com.sprouttrail.image.model.ImageGenerationRecord;
import com.sprouttrail.image.mapper.UserMapper;
import com.sprouttrail.image.model.User;
import com.sprouttrail.image.service.UserAuthService;
import jakarta.annotation.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** 用户名密码校验及 Sa-Token 会话操作的默认实现。 */
@Service
public class UserAuthServiceImpl implements UserAuthService {

    @Resource
    private UserMapper userMapper;

    @Resource
    private PasswordEncoder passwordEncoder;

    /** {@inheritDoc} */
    @Override
    public ImageGenerationRecord.LoginResponse login(GeneratedImage.LoginRequest request) {
        String username = request.getUsername().trim();
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username));

        // 用户不存在、账号禁用和密码错误统一返回相同提示，避免泄露账号状态。
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())
                || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }

        StpUtil.login(user.getId());
        return new ImageGenerationRecord.LoginResponse(StpUtil.getTokenName(), StpUtil.getTokenValue(),
                user.getId(), user.getUsername(), StpUtil.getTokenTimeout());
    }

    /** {@inheritDoc} */
    @Override
    public void logout() {
        StpUtil.logout();
    }

    /** {@inheritDoc} */
    @Override
    public ImageAspectRatio.TokenResponse getCurrentToken() {
        Long userId = StpUtil.getLoginIdAsLong();
        User user = userMapper.selectById(userId);

        // 用户可能在登录后被管理员禁用或从数据库移除，查询会话时同步结束该会话。
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            StpUtil.logout();
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录状态已失效，请重新登录");
        }

        return new ImageAspectRatio.TokenResponse(StpUtil.getTokenName(), StpUtil.getTokenValue(),
                user.getId(), user.getUsername(), StpUtil.getTokenTimeout());
    }
}
