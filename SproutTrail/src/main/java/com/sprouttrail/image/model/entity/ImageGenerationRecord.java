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
 * 文生图记录
 */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@TableName("image_generation_record")
public class ImageGenerationRecord {

    /** 数据库自增主键。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 用户输入且不做修改的原始提示词。 */
    @TableField("original_prompt")
    private String originalPrompt;

    /** 最终提交给图片模型的完整提示词。 */
    @TableField("optimized_prompt")
    private String optimizedPrompt;

    /** 上传完成后由 MinIO 返回的图片地址。 */
    @TableField("minio_image_url")
    private String minioImageUrl;

    /** 本次生成使用的图片画幅枚举名称。 */
    @TableField("aspect_ratio")
    private String aspectRatio;

    /** 由 MySQL 默认值写入的记录创建时间。 */
    @TableField("created_at")
    private LocalDateTime createdAt;

}
