-- 已有业务库但还没有 graph_build_task 表时执行；不要重放 tables.sql。
CREATE TABLE IF NOT EXISTS `graph_build_task` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '任务ID',
    `document_id` BIGINT NOT NULL COMMENT '知识文档ID',
    `version_id` BIGINT NOT NULL COMMENT '知识文档版本ID',
    `status` VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/SUCCEEDED/FAILED',
    `last_error` VARCHAR(1024) NULL COMMENT '最近一次失败的异常类型',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最近状态变更时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_graph_version` (`version_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档图谱构建任务';
