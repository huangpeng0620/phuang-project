package com.phuang.service;

import com.phuang.model.dto.LoginDTO;
import com.phuang.model.dto.StaffLoginDTO;
import com.phuang.model.vo.LoginUserVO;

/**
 *
 * @description AuthService
 * @author huangpeng
 * @since 2026/9/13
 */

public interface AuthService {

    /**
     * 登录
     *
     * @param loginDTO 登录参数（工号 + 密码）
     * @return 登录用户信息（含 token）
     */
    LoginUserVO login(LoginDTO loginDTO);

    /**
     * 登出
     */
    void logout();

    /**
     * 获取当前登录用户信息
     *
     * @return 登录用户信息
     */
    LoginUserVO getCurrentUser();

    /**
     * 员工登录（工号 + 姓名）
     *
     * @param staffLoginDTO 员工登录参数
     * @return 登录用户信息（含 token）
     */
    LoginUserVO staffLogin(StaffLoginDTO staffLoginDTO);

    /**
     * 获取当前登录用户ID
     *
     * @return 用户ID
     */
    String getCurrentUserId();

    /**
     * 当前登录是否为员工登录
     */
    boolean isStaffLogin();
}
