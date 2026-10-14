package com.sprouttrail.image.model.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/** 图片优化历史分页响应。 */
@Getter
@AllArgsConstructor
public class ImageOptimizationHistoryResponse {

    /** 当前页的历史记录。 */
    private final List<ImageOptimizationHistoryItem> items;

    /** 当前页码，从 1 开始。 */
    private final int page;

    /** 当前页大小。 */
    private final int pageSize;

    /** 是否还有下一页。 */
    private final boolean hasMore;
}
