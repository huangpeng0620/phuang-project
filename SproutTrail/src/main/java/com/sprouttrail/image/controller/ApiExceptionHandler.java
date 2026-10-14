package com.sprouttrail.image.controller;

import com.sprouttrail.image.exception.ImageWorkflowException;
import com.sprouttrail.image.model.ApiError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 将后端异常转换为前端可以稳定处理的 JSON 错误结构
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /**
     * 返回 Bean Validation 产生的第一个用户可读错误
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiError> handleValidation(BindException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.isBindingFailure() || error.getDefaultMessage() == null
                        ? "请求参数不合法，请检查画面比例等字段" : error.getDefaultMessage())
                .orElse("请求参数不合法");
        return ResponseEntity.badRequest()
                .body(ApiError.of("VALIDATION_FAILED", message));
    }

    /** 上传超过 Spring multipart 限制时，返回与其他接口一致的错误结构。 */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleUploadTooLarge() {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiError.of("IMAGE_UPLOAD_TOO_LARGE", "原图不能超过 5MB"));
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
     * 根据统一业务异常携带的状态码和错误码生成错误响应。
     */
    @ExceptionHandler(ImageWorkflowException.class)
    public ResponseEntity<ApiError> handleApplication(ImageWorkflowException exception) {
        // 不输出底层异常详情，避免 SDK 把用户提示词等请求内容写入日志。
        Throwable cause = exception.getCause();
        log.error("Application operation failed: code={}, causeType={}",
                exception.code(), cause == null ? "none" : cause.getClass().getSimpleName());
        return ResponseEntity.status(exception.status())
                .body(ApiError.of(exception.code(), exception.userMessage()));
    }
}
