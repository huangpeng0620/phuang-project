CREATE TABLE IF NOT EXISTS image_generation_record (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    original_prompt VARCHAR(800) NOT NULL COMMENT '用户输入的原始提示词',
    optimized_prompt VARCHAR(2048) NOT NULL COMMENT '最终提交给图片模型的提示词',
    minio_image_url VARCHAR(2048) NOT NULL COMMENT '生成图片的 MinIO 地址',
    aspect_ratio VARCHAR(32) NOT NULL COMMENT '图片画幅',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '生成时间',
    PRIMARY KEY (id),
    KEY idx_image_generation_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='图片生成记录';
