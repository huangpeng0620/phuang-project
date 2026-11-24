package com.phuang.bussiness.auth.entity;

import java.util.Locale;

/**
 * 登录账号支持的角色，枚举名称对应数据库中的角色值
 */
public enum UserRole {
    USER,
    ADMIN;

    /**
     * 将接口或数据库里的角色名称转成枚举，兼容不同大小写
     * @param name
     * @return
     */
    public static UserRole fromName(String name) {
        return valueOf(name.toUpperCase(Locale.ROOT));
    }
}
