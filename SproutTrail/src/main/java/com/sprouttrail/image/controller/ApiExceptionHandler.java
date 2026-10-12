package com.sprouttrail.image.controller;

import com.sprouttrail.image.exception.ImageGenerationException;
import com.sprouttrail.image.exception.ImageStorageException;
import com.sprouttrail.image.model.ApiError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 将后端异常转换为前端可以稳定处理的 JSON 错误结构
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /**
     * 返回 Bean Validation 产生的第一个用户可读错误
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage() == null ? "请求参数不合法" : error.getDefaultMessage())
                .orElse("请求参数不合法");
        return ResponseEntity.badRequest()
                .body(ApiError.of("VALIDATION_FAILED", message));
    }

    /**
     * 处理 JSON 格式错误或缺失请求体
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableRequest() {
        return ResponseEntity.badRequest()
                .body(ApiError.of("INVALID_REQUEST", "请求内容格式不正确"));
    }

    /**
     * 上游 AI 服务失败时返回 502，表明应用正常但依赖服务未成功响应
     */
    @ExceptionHandler(ImageGenerationException.class)
    public ResponseEntity<ApiError> handleImageGeneration(ImageGenerationException exception) {
        log.error("Image generation failed", exception);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiError.of("IMAGE_GENERATION_FAILED", "图片生成失败，请稍后重试"));
    }

    /**
     * 本地文件系统写入失败时返回服务器错误
     */
    @ExceptionHandler(ImageStorageException.class)
    public ResponseEntity<ApiError> handleImageStorage(ImageStorageException exception) {
        log.error("Image storage failed", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of("IMAGE_STORAGE_FAILED", "图片已生成，但保存失败"));
    }
}
