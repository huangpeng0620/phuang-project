package com.phuang.bussiness.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户登录账号实体，对应 user_account 表。
 * userId 关联 user_profile.user_id，是 sa-token 登录后存储的 loginId
 *
 * @author huangpeng
 */
@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
@TableName("user_account")
public class UserAccount {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 关联 user_profile.user_id，也是 sa-token 的 loginId */
    private String userId;

    /** 登录账号 */
    private String username;

    /** 登录密码（生产环境应使用 BCrypt 存储） */
    private String password;

    /** 用户真实姓名 */
    private String realName;

    /** 角色：USER 普通用户 / ADMIN 管理员 */
    private String role;

    private LocalDateTime createdTime;

    /**
     * 是否为管理员（role = ADMIN，忽略大小写）
     * @return
     */
    public boolean isAdmin() {
        return "ADMIN".equalsIgnoreCase(role);
    }

}
