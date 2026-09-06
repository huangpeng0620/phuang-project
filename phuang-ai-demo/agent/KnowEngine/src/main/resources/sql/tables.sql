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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识片段表';

-- 表元数据表（存储动态创建的表的元数据信息）
create TABLE `table_meta` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT comment '主键ID',
    `table_name`   VARCHAR(128) NOT NULL comment '表名',
    `description`  VARCHAR(512) NULL     comment '表描述',
    `create_sql`   TEXT         NULL     comment '建表语句',
    `columns_info` TEXT         NULL     comment '字段信息（JSON格式）',
    `version_id`   BIGINT       NULL     comment '关联的文档版本ID（knowledge_document_version.version_id），DATA_QUERY 多版本管理使用',
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP comment '创建时间',
    `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON update CURRENT_TIMESTAMP comment '修改时间',
    `lock_version` INT          NOT NULL DEFAULT 0 comment '乐观锁版本号',
    `deleted`      TINYINT      NOT NULL DEFAULT 0 comment '是否删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`),
    -- 表名唯一索引
    UNIQUE INDEX `uk_table_name` (`table_name`),
    -- 版本ID索引，用于按版本清理与查询
    INDEX `idx_version_id` (`version_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci comment = '表元数据表';

-- AI对话会话表
create TABLE `chat_conversation` (
    `id`              BIGINT      NOT NULL AUTO_INCREMENT comment '主键ID',
    `conversation_id` VARCHAR(64) NOT NULL comment '会话唯一标识',
    `user_id`         VARCHAR(64) NOT NULL comment '用户ID',
    `title`           VARCHAR(512) NULL    comment '会话标题',
    `status`          VARCHAR(32) NOT NULL DEFAULT 'active' comment '状态',
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP comment '创建时间',
    `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON update CURRENT_TIMESTAMP comment '修改时间',
    `lock_version` INT          NOT NULL DEFAULT 0 comment '乐观锁版本号',
    `deleted`      TINYINT      NOT NULL DEFAULT 0 comment '是否删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_conversation_id` (`conversation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci comment = 'AI对话会话表';

-- AI对话消息表
create TABLE `chat_message` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT comment '主键ID',
    `message_id`       VARCHAR(64)  NOT NULL comment '消息唯一标识',
    `conversation_id`  VARCHAR(64)  NOT NULL comment '所属会话ID',
    `type`             VARCHAR(32)  NOT NULL comment '角色：USER/ASSISTANT',
    `content`          LONGTEXT     NULL     comment '消息内容',
    `transform_content` LONGTEXT    NULL     comment '改写后的内容',
    `token_count`      INT          NULL     comment 'Token数量',
    `model_name`       VARCHAR(128) NULL     comment '使用的模型名称',
    `rag_references`   JSON         NULL     comment 'RAG引用内容JSON数组，包含document_id、document_title、chunk_id、chunk_content、similarity_score、retrieval_source等字段',
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP comment '创建时间',
    `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON update CURRENT_TIMESTAMP comment '修改时间',
    `lock_version` INT          NOT NULL DEFAULT 0 comment '乐观锁版本号',
    `deleted`      TINYINT      NOT NULL DEFAULT 0 comment '是否删除：0-未删除，1-已删除',
    `metadata`         JSON         NULL     comment '扩展元数据JSON格式',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_message_id` (`message_id`),
    INDEX `idx_conversation_id` (`conversation_id`),
    INDEX `idx_create_time` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci comment = 'AI对话消息表';