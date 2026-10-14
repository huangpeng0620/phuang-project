package com.sprouttrail.auth.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sprouttrail.auth.mapper.UserMapper;
import com.sprouttrail.auth.model.entity.User;
import com.sprouttrail.auth.model.request.LoginRequest;
import com.sprouttrail.auth.model.request.RegisterRequest;
import com.sprouttrail.auth.model.response.LoginResponse;
import com.sprouttrail.auth.model.response.TokenResponse;
import com.sprouttrail.auth.service.UserAuthService;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;

/** 用户名密码校验及 Sa-Token 会话操作的默认实现。 */
@Service
public class UserAuthServiceImpl implements UserAuthService {

    @Resource
    private UserMapper userMapper;

    @Resource
    private PasswordEncoder passwordEncoder;

    /** {@inheritDoc} */
    @Override
    public void register(RegisterRequest request) {
        String username = request.getUsername().trim();
        String password = request.getPassword();

        if (username.length() < 3 || username.length() > 64) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名长度需为 3～64 个字符");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码不能超过 72 个字节");
        }
        if (userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username)) > 0) {
            throw usernameAlreadyExists();
        }

        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setStatus(1);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException exception) {
            // 数据库唯一索引处理并发注册时的用户名冲突。
            throw usernameAlreadyExists();
        }
    }

    /** {@inheritDoc} */
    @Override
    public LoginResponse login(LoginRequest request) {
        String username = request.getUsername().trim();
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username));

        // 用户不存在、账号禁用和密码错误统一返回相同提示，避免泄露账号状态。
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())
                || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }

        StpUtil.login(user.getId());
        return new LoginResponse(StpUtil.getTokenName(), StpUtil.getTokenValue(),
                user.getId(), user.getUsername(), StpUtil.getTokenTimeout());
    }

    /** {@inheritDoc} */
    @Override
    public void logout() {
        StpUtil.logout();
    }

    /** {@inheritDoc} */
    @Override
    public TokenResponse getCurrentToken() {
        Long userId = StpUtil.getLoginIdAsLong();
        User user = userMapper.selectById(userId);

        // 用户可能在登录后被管理员禁用或从数据库移除，查询会话时同步结束该会话。
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            StpUtil.logout();
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录状态已失效，请重新登录");
        }

        return new TokenResponse(StpUtil.getTokenName(), StpUtil.getTokenValue(),
                user.getId(), user.getUsername(), StpUtil.getTokenTimeout());
    }

    private ResponseStatusException usernameAlreadyExists() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "该用户名已被注册");
    }
}
