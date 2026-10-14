package com.sprouttrail.image.model.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/** 历史列表中展示的一条文生图记录。 */
@Getter
@AllArgsConstructor
public class ImageHistoryItem {

    /** 图片生成记录 ID。 */
    private final Long id;

    /** 用户最初输入的提示词。 */
    private final String originalPrompt;

    /** 最终提交给图片模型的完整提示词。 */
    private final String optimizedPrompt;

    /** MinIO 图片地址。 */
    private final String minioImageUrl;

    /** 生成时使用的画面比例。 */
    private final String aspectRatio;

    /** 图片生成时间。 */
    private final LocalDateTime createdAt;
}
