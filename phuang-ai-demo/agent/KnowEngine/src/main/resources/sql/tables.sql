-- 知识文档表
create TABLE `knowledge_document` (
    `doc_id`        BIGINT        NOT NULL AUTO_INCREMENT comment '文档ID',
    `doc_title`     VARCHAR(1024) NOT NULL comment '文档标题',
    `status`        VARCHAR(32)   NOT NULL comment '状态：INIT, UPLOADED, CONVERTING, CONVERTED, CHUNKED, VECTOR_STORED',
    `accessible_by` VARCHAR(1024) NULL     comment '可见范围',
    `description`   VARCHAR(512)  NULL     comment '文档描述',
    `knowledge_base_type` VARCHAR(32) NULL comment '知识库类型：DOCUMENT_SEARCH, DATA_QUERY',
    `extension`     TEXT          NULL     comment '扩展字段，保存JSON字符串',
    `current_version_id` BIGINT  NULL     comment '当前激活版本ID，指向 knowledge_document_version.version_id',
    `created_at`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP comment '创建时间',
    `updated_at`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON update CURRENT_TIMESTAMP comment '修改时间',
    `lock_version` INT           NOT NULL DEFAULT 0 comment '乐观锁版本号',
    `deleted`       TINYINT      NOT NULL DEFAULT 0 comment '是否删除：0-未删除，1-已删除',
    PRIMARY KEY (`doc_id`),
    -- 为状态字段添加索引,优化定时任务扫表性能
    INDEX `idx_status` (`status`),
    -- 复合索引: 状态+文档ID,优化分页查询性能
    INDEX `idx_status_doc_id` (`status`, `doc_id`),
    -- 创建时间索引，优化按时间排序查询
    INDEX `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci comment = '知识文档表';

-- 文档版本表（存储文档每个版本的快照信息）
create TABLE `knowledge_document_version` (
    `version_id`      BIGINT       NOT NULL AUTO_INCREMENT comment '版本ID',
    `doc_id`          BIGINT       NOT NULL comment '关联文档ID（knowledge_document.doc_id）',
    `version`         VARCHAR(32)  NOT NULL DEFAULT '1.0.0' comment '版本号（语义化版本，如 1.0.0）',
    `doc_url`         VARCHAR(2048) NULL    comment '该版本文档URL（MinIO原始文件）',
    `converted_doc_url` VARCHAR(2048) NULL  comment '该版本转换后的文档URL',
    `content_hash`    VARCHAR(64)  NULL    comment '该版本文档内容哈希值（SHA-256）',
    `status`          VARCHAR(32)  NOT NULL comment '版本状态：UPLOADED, CONVERTING, CONVERTED, CHUNKED, VECTOR_STORED, STORED',
    `upload_user`     VARCHAR(255) NULL    comment '该版本上传用户',
    `changelog`       VARCHAR(1024) NULL   comment '版本变更说明',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP comment '创建时间',
    `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON update CURRENT_TIMESTAMP comment '修改时间',
    `lock_version`   INT          NOT NULL DEFAULT 0 comment '乐观锁版本号',
    `deleted`        TINYINT      NOT NULL DEFAULT 0 comment '是否删除：0-未删除，1-已删除',
    PRIMARY KEY (`version_id`),
    -- 同一文档版本号唯一
    UNIQUE KEY `uk_doc_version` (`doc_id`, `version`),
    -- 文档ID索引，用于查询文档所有版本
    INDEX `idx_doc_id` (`doc_id`),
    -- 内容哈希索引，用于跨版本去重
    INDEX `idx_content_hash` (`content_hash`)
)  ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_cicomment = '文档版本表';

-- 知识片段表
create TABLE `knowledge_segment` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT comment '片段ID',
    `text`        LONGTEXT     NOT NULL comment '文本内容',
    `chunk_id`    VARCHAR(255) NULL     comment '分片ID',
    `metadata`    VARCHAR(2048) NULL     comment '元数据',
    `document_id` BIGINT       NOT NULL comment '所属文档ID',
    `document_version` BIGINT       NULL     comment '所属文档版本ID（knowledge_document_version.version_id）',
    `chunk_order` INT       NOT NULL comment '顺序',
    `embedding_id` VARCHAR(255) NULL     comment '嵌入ID',
    `status` VARCHAR(255) NULL     comment '状态：STORED, VECTOR_STORED',
    `skip_embedding` INT NULL     comment '是否跳过嵌入生成',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP comment '创建时间',
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON update CURRENT_TIMESTAMP comment '修改时间',
    `lock_version`  INT          NOT NULL DEFAULT 0 comment '乐观锁版本号',
    `deleted`       TINYINT      NOT NULL DEFAULT 0 comment '是否删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`),
    -- 文档ID索引
    INDEX `idx_document_id` (`document_id`),
    -- 复合索引：文档ID+顺序，优化按文档查询并排序
    INDEX `idx_document_id_chunk_order` (`document_id`, `chunk_order`),
    -- 复合索引：文档ID+状态+跳过嵌入，优化向量化补偿任务查询
    INDEX `idx_document_status_skip` (`document_id`, `status`, `skip_embedding`),
    -- 文档版本ID索引，优化按版本查询分段
    INDEX `idx_document_version` (`document_version`),
    -- 状态索引，优化按状态查询
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='知识片段表';