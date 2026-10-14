package com.sprouttrail.auth.controller;


import com.sprouttrail.auth.model.request.LoginRequest;
import com.sprouttrail.auth.model.request.RegisterRequest;
import com.sprouttrail.auth.model.response.LoginResponse;
import com.sprouttrail.auth.model.response.TokenResponse;
import com.sprouttrail.auth.service.UserAuthService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 提供注册、登录、退出和当前 token 查询接口
 */
@RestController
@RequestMapping("/api/auth")
public class UserAuthController {

    @Resource
    private UserAuthService userAuthService;

    /**
     * 创建普通用户账号；新账号创建后需要通过登录接口获取 token
     * @param request
     * @return
     */
    @PostMapping(value = "/register", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest request) {
        userAuthService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * 使用用户名和密码登录，并返回 Sa-Token 凭证
     * @param request
     * @return
     */
    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(userAuthService.login(request));
    }

    /**
     * 注销当前 token 对应的会话
     */
    @PostMapping("/logout")
    public void logout() {
        userAuthService.logout();
    }

    /**
     * 校验当前 token 并返回 token 和登录用户信息
     * @return
     */
    @GetMapping("/token")
    public ResponseEntity<TokenResponse> getToken() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(userAuthService.getCurrentToken());
    }
}
