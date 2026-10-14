package com.sprouttrail.image.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sprouttrail.common.exception.ImageWorkflowException;
import com.sprouttrail.image.mapper.ImageGenerationRecordMapper;
import com.sprouttrail.image.mapper.ImageOptimizationRecordMapper;
import com.sprouttrail.image.model.entity.ImageGenerationRecord;
import com.sprouttrail.image.model.entity.ImageOptimizationRecord;
import com.sprouttrail.image.model.response.GeneratedImage;
import com.sprouttrail.image.model.response.ImageHistoryItem;
import com.sprouttrail.image.model.response.ImageHistoryResponse;
import com.sprouttrail.image.model.response.ImageOptimizationHistoryItem;
import com.sprouttrail.image.model.response.ImageOptimizationHistoryResponse;
import com.sprouttrail.image.service.ImageHistoryService;
import com.sprouttrail.storage.MinioService;
import jakarta.annotation.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStream;
import java.net.URI;
import java.util.List;

/**
 * 查询用户的图片生成、图片优化历史，并从 MinIO 下载历史结果。
 */
@Service
public class ImageHistoryServiceImpl implements ImageHistoryService {

    @Resource
    private ImageGenerationRecordMapper imageGenerationRecordMapper;

    @Resource
    private ImageOptimizationRecordMapper imageOptimizationRecordMapper;

    @Resource
    private MinioService minioService;

    /** 只查询当前用户拥有的文生图记录，并多取一条用于判断是否还有下一页。 */
    @Override
    public ImageHistoryResponse getHistory(Long userId, int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "页码需大于 0，页大小需为 1～50");
        }

        long offset = ((long) page - 1) * pageSize;
        List<ImageGenerationRecord> records = imageGenerationRecordMapper.selectList(
                new LambdaQueryWrapper<ImageGenerationRecord>()
                        .eq(ImageGenerationRecord::getUserId, userId)
                        .orderByDesc(ImageGenerationRecord::getCreatedAt)
                        .orderByDesc(ImageGenerationRecord::getId)
                        .last("LIMIT " + (pageSize + 1) + " OFFSET " + offset));

        boolean hasMore = records.size() > pageSize;
        List<ImageGenerationRecord> pageRecords = hasMore
                ? records.subList(0, pageSize)
                : records;
        List<ImageHistoryItem> items = pageRecords.stream()
                .map(record -> new ImageHistoryItem(record.getId(), record.getOriginalPrompt(),
                        record.getOptimizedPrompt(), record.getMinioImageUrl(), record.getAspectRatio(),
                        record.getCreatedAt()))
                .toList();
        return new ImageHistoryResponse(items, page, pageSize, hasMore);
    }

    /** 下载前按用户 ID 过滤，避免通过猜测记录 ID 获取其他用户的图片。 */
    @Override
    public GeneratedImage downloadHistoryImage(Long userId, Long recordId) {
        ImageGenerationRecord record = imageGenerationRecordMapper.selectOne(
                new LambdaQueryWrapper<ImageGenerationRecord>()
                        .eq(ImageGenerationRecord::getId, recordId)
                        .eq(ImageGenerationRecord::getUserId, userId));
        if (record == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "没有找到这张图片");
        }

        try {
            String imagePath = URI.create(record.getMinioImageUrl()).getPath();
            String filename = imagePath.substring(imagePath.lastIndexOf('/') + 1);
            if (!filename.matches("[a-fA-F0-9-]{36}\\.png")) {
                throw new ImageWorkflowException("IMAGE_STORAGE_FAILED", HttpStatus.BAD_GATEWAY,
                        "图片地址无效，暂时无法下载");
            }
            try (InputStream stream = minioService.downloadFile(filename)) {
                return new GeneratedImage(filename, stream.readAllBytes(), record.getMinioImageUrl());
            }
        } catch (ImageWorkflowException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ImageWorkflowException("IMAGE_STORAGE_FAILED", HttpStatus.BAD_GATEWAY,
                    "图片下载失败，请稍后重试", exception);
        }
    }

    /** 只查询当前用户拥有的图片优化记录，并多取一条用于判断是否还有下一页。 */
    @Override
    public ImageOptimizationHistoryResponse getOptimizationHistory(Long userId, int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "页码需大于 0，页大小需为 1～50");
        }

        long offset = ((long) page - 1) * pageSize;
        List<ImageOptimizationRecord> records = imageOptimizationRecordMapper.selectList(
                new LambdaQueryWrapper<ImageOptimizationRecord>()
                        .eq(ImageOptimizationRecord::getUserId, userId)
                        .orderByDesc(ImageOptimizationRecord::getCreatedAt)
                        .orderByDesc(ImageOptimizationRecord::getId)
                        .last("LIMIT " + (pageSize + 1) + " OFFSET " + offset));

        boolean hasMore = records.size() > pageSize;
        List<ImageOptimizationRecord> pageRecords = hasMore
                ? records.subList(0, pageSize)
                : records;
        List<ImageOptimizationHistoryItem> items = pageRecords.stream()
                .map(record -> new ImageOptimizationHistoryItem(record.getId(), record.getPrompt(),
                        record.getSourceImageUrl(), record.getResultImageUrl(), record.getAspectRatio(),
                        record.getCreatedAt()))
                .toList();
        return new ImageOptimizationHistoryResponse(items, page, pageSize, hasMore);
    }

    /** 下载前按用户 ID 过滤，避免通过猜测记录 ID 获取其他用户的优化结果。 */
    @Override
    public GeneratedImage downloadOptimizationHistoryImage(Long userId, Long recordId) {
        ImageOptimizationRecord record = imageOptimizationRecordMapper.selectOne(
                new LambdaQueryWrapper<ImageOptimizationRecord>()
                        .eq(ImageOptimizationRecord::getId, recordId)
                        .eq(ImageOptimizationRecord::getUserId, userId));
        if (record == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "没有找到这张图片");
        }

        try {
            String imagePath = URI.create(record.getResultImageUrl()).getPath();
            String filename = imagePath.substring(imagePath.lastIndexOf('/') + 1);
            if (!filename.matches("[a-fA-F0-9-]{36}\\.png")) {
                throw new ImageWorkflowException("IMAGE_STORAGE_FAILED", HttpStatus.BAD_GATEWAY,
                        "图片地址无效，暂时无法下载");
            }
            try (InputStream stream = minioService.downloadFile(filename)) {
                return new GeneratedImage(filename, stream.readAllBytes(), record.getResultImageUrl());
            }
        } catch (ImageWorkflowException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ImageWorkflowException("IMAGE_STORAGE_FAILED", HttpStatus.BAD_GATEWAY,
                    "图片下载失败，请稍后重试", exception);
        }
    }
}
