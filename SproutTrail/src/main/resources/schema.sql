CREATE TABLE IF NOT EXISTS `user` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    username VARCHAR(64) NOT NULL COMMENT '登录用户名',
    password_hash VARCHAR(100) NOT NULL COMMENT 'BCrypt 密码哈希，不保存明文密码',
    status TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '账号状态：1 启用，0 禁用',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目用户';

CREATE TABLE IF NOT EXISTS image_generation_record (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    user_id BIGINT UNSIGNED NOT NULL COMMENT '所属用户ID',
    original_prompt VARCHAR(800) NOT NULL COMMENT '用户输入的原始提示词',
    optimized_prompt VARCHAR(2048) NOT NULL COMMENT '最终提交给图片模型的提示词',
    minio_image_url VARCHAR(2048) NOT NULL COMMENT '生成图片的 MinIO 地址',
    aspect_ratio VARCHAR(32) NOT NULL COMMENT '图片画幅',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '生成时间',
    PRIMARY KEY (id),
    KEY idx_image_generation_user_created (user_id, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='图片生成记录';

CREATE TABLE IF NOT EXISTS image_optimization_record (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    user_id BIGINT UNSIGNED NOT NULL COMMENT '所属用户ID',
    source_image_url VARCHAR(2048) NOT NULL COMMENT '上传原图的 MinIO 地址',
    prompt VARCHAR(800) NOT NULL COMMENT '用户提交的图片优化要求',
    result_image_url VARCHAR(2048) NOT NULL COMMENT '优化结果的 MinIO 地址',
    aspect_ratio VARCHAR(32) NOT NULL COMMENT '输出画幅',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '优化时间',
    PRIMARY KEY (id),
    KEY idx_image_optimization_user_created (user_id, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='图片优化记录';
