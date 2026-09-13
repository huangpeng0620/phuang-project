package com.phuang.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.phuang.model.dto.LoginDTO;
import com.phuang.model.dto.StaffLoginDTO;
import com.phuang.model.vo.LoginUserVO;
import com.phuang.model.vo.Result;
import com.phuang.service.AuthService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

/**
 * 认证接口
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    @Resource
    private AuthService authService;

    /**
     * 登录
     */
    @PostMapping("/login")
    public Result<LoginUserVO> login(@RequestBody LoginDTO loginDTO) {
        LoginUserVO user = authService.login(loginDTO);
        return Result.success(user);
    }

    /**
     * 员工登录（工号 + 姓名）
     */
    @PostMapping("/staffLogin")
    public Result<LoginUserVO> staffLogin(@RequestBody StaffLoginDTO staffLoginDTO) {
        LoginUserVO user = authService.staffLogin(staffLoginDTO);
        return Result.success(user);
    }

    /**
     * 登出
     */
    @PostMapping("/logout")
    public Result<Void> logout() {
        authService.logout();
        return Result.success();
    }

    /**
     * 获取当前登录用户信息
     */
    @GetMapping("/userInfo")
    public Result<LoginUserVO> userInfo() {
        return Result.success(authService.getCurrentUser());
    }

    /**
     * 是否已登录
     */
    @GetMapping("/isLogin")
    public Result<Boolean> isLogin() {
        return Result.success(StpUtil.isLogin());
    }
}
