package com.sprouttrail.image.model.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/** 历史列表中展示的一条图片优化记录。 */
@Getter
@AllArgsConstructor
public class ImageOptimizationHistoryItem {

    /** 图片优化记录 ID。 */
    private final Long id;

    /** 用户提交的优化要求。 */
    private final String prompt;

    /** 用户上传原图的 MinIO 地址。 */
    private final String sourceImageUrl;

    /** 优化结果图片的 MinIO 地址。 */
    private final String resultImageUrl;

    /** 输出画幅。 */
    private final String aspectRatio;

    /** 图片优化时间。 */
    private final LocalDateTime createdAt;
}
