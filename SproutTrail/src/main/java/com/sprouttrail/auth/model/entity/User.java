package com.sprouttrail.auth.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 项目登录用户。
 *
 * <p>表名 {@code user} 与数据库常用函数同名，因此在映射中使用反引号保护标识符。
 * 密码字段只保存 BCrypt 哈希，不保存用户提交的明文密码。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("`user`")
public class User {

    /** 用户主键，同时作为 Sa-Token 的登录 ID。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户名，数据库通过唯一索引保证不重复。 */
    private String username;

    /** BCrypt 密码哈希。 */
    @TableField("password_hash")
    private String passwordHash;

    /** 账号状态：1 启用，0 禁用。 */
    private Integer status;
}
