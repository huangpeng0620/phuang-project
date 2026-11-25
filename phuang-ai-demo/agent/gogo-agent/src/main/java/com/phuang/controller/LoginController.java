package com.phuang.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.phuang.bussiness.auth.service.AuthService;
import com.phuang.model.request.LoginRequest;
import com.phuang.model.request.RegisterRequest;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * 认证接口：登录、管理员建号、退出、获取当前用户信息
 * <p>
 *     /api/auth/login 无需鉴权，其余接口需登录态
 * </p>
 *
 * @author huangpeng
 */
@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class LoginController {

    @Resource
    private AuthService authService;

    /**
     * 用户登录
     * 成功后返回 Token，前端后续所有请求需在 Header 中携带：
     * {@code Authorization: <token>}
     */
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest request) {
        String token = authService.login(request.getUsername(), request.getPassword());
        return Map.of("token", token,
                "tokenName", StpUtil.getTokenName()
        );
    }

    /** 管理员可以创建 USER 或 ADMIN 账号。成功时只返回新账号的标识。 */
    @PostMapping("/register")
    @SaCheckLogin
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> register(@Valid @RequestBody RegisterRequest request) {
        if (!authService.isAdmin(StpUtil.getLoginIdAsString())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅管理员可以创建账号");
        }
        try {
            authService.register(request.getUserId(), request.getUsername(), request.getPassword(), request.getRealName(), request.getRole());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "user_id 或 username 已存在");
        }
        return Map.of("user_id", request.getUserId(), "username", request.getUsername());
    }

    /**
     * 退出登录，销毁当前 Token 对应的 sa-token 会话
     */
    @PostMapping("/logout")
    @SaCheckLogin
    public Map<String, String> logout() {
        authService.logout();
        return Map.of("message", "退出成功");
    }

    /**
     * 获取当前已登录用户信息
     */
    @GetMapping("/info")
    @SaCheckLogin
    public Map<String, Object> info() {
        String userId = StpUtil.getLoginIdAsString();
        return Map.of("userId", userId,
                "admin", authService.isAdmin(userId)
        );
    }
}
