-- GoGo 差旅助手数据库初始化脚本
-- 请在 MySQL 中先执行 CREATE DATABASE gogo_travel DEFAULT CHARACTER SET utf8mb4;

CREATE TABLE IF NOT EXISTS `user_account` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `user_id`      VARCHAR(64)  NOT NULL COMMENT '关联 user_profile.user_id',
    `username`     VARCHAR(64)  NOT NULL COMMENT '登录账号',
    `password`     VARCHAR(128) NOT NULL COMMENT '登录密码（Bcrypt 加密）',
    `real_name`    VARCHAR(64)  DEFAULT NULL COMMENT '用户真实姓名',
    `role`         VARCHAR(16)  NOT NULL DEFAULT 'USER' COMMENT '角色: USER 普通用户 / ADMIN 管理员',
    `created_time` DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_id`  (`user_id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户登录账号表';

-- ============================================================
-- 测试数据（本地开发/演示用，生产环境按需清空）
-- ============================================================




