package com.sprouttrail.image.service;

import com.sprouttrail.image.model.response.GeneratedImage;
import com.sprouttrail.image.model.response.ImageHistoryResponse;
import com.sprouttrail.image.model.response.ImageOptimizationHistoryResponse;

/** 定义当前用户图片历史的查询和下载操作。 */
public interface ImageHistoryService {

    /** 查询指定用户的文生图历史记录。 */
    ImageHistoryResponse getHistory(Long userId, int page, int pageSize);

    /** 下载当前用户拥有的文生图历史图片。 */
    GeneratedImage downloadHistoryImage(Long userId, Long recordId);

    /** 查询指定用户的图片优化历史记录。 */
    ImageOptimizationHistoryResponse getOptimizationHistory(Long userId, int page, int pageSize);

    /** 下载当前用户拥有的图片优化结果。 */
    GeneratedImage downloadOptimizationHistoryImage(Long userId, Long recordId);
}
