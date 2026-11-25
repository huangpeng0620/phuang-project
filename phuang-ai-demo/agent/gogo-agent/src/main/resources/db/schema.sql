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
-- 对话与消息历史（前端历史会话展示）
-- ============================================================

CREATE TABLE IF NOT EXISTS `chat_conversation` (
    `conversation_id` VARCHAR(64)  NOT NULL COMMENT '会话ID（同前端 sessionId）',
    `user_id`         VARCHAR(64)  NOT NULL COMMENT '用户ID',
    `title`           VARCHAR(256) NOT NULL DEFAULT '新对话' COMMENT '会话标题',
    `created_at`      DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    `deleted`         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除标志',
    PRIMARY KEY (`conversation_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_updated_at` (`updated_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '对话会话';

CREATE TABLE IF NOT EXISTS `chat_message` (
    `message_id`      VARCHAR(64)  NOT NULL COMMENT '消息ID',
    `conversation_id` VARCHAR(64)  NOT NULL COMMENT '会话ID',
    `role`            VARCHAR(32)  NOT NULL COMMENT '角色：user/agent/system',
    `content`         TEXT         DEFAULT NULL COMMENT '消息内容',
    `agent_name`      VARCHAR(128) DEFAULT NULL COMMENT 'Agent名称（role=agent 时）',
    `extra`           JSON         DEFAULT NULL COMMENT '扩展信息（进度快照/推荐问题等）',
    `feedback`        VARCHAR(16)  DEFAULT NULL COMMENT '用户反馈：LIKE 点赞 / DISLIKE 点踩 / NULL 未反馈',
    `feedback_at`     DATETIME     DEFAULT NULL COMMENT '反馈时间',
    `created_at`      DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `deleted`         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除标志',
    PRIMARY KEY (`message_id`),
    KEY `idx_conversation_id` (`conversation_id`),
    KEY `idx_created_at` (`created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '对话消息';

-- ============================================================
-- 测试数据（本地开发/演示用，生产环境按需清空）
-- ============================================================




