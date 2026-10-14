package com.sprouttrail.image.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 图片优化历史记录
 */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@TableName("image_optimization_record")
public class ImageOptimizationRecord {

    /** 数据库自增主键。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 创建记录的用户 ID。 */
    @TableField("user_id")
    private Long userId;

    /** 上传原图在 MinIO 中的地址。 */
    @TableField("source_image_url")
    private String sourceImageUrl;

    /** 用户提交的图片优化要求。 */
    @TableField("prompt")
    private String prompt;

    /** 优化结果在 MinIO 中的地址。 */
    @TableField("result_image_url")
    private String resultImageUrl;

    /** 输出图片画幅。 */
    @TableField("aspect_ratio")
    private String aspectRatio;

    /** 由 MySQL 默认值写入的记录创建时间。 */
    @TableField("created_at")
    private LocalDateTime createdAt;
}
