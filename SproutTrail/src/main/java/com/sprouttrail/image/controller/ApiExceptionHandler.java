package com.sprouttrail.image.controller;

import cn.dev33.satoken.exception.NotLoginException;
import com.sprouttrail.image.exception.ImageWorkflowException;
import com.sprouttrail.image.model.ApiError;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 将后端异常转换为前端可以稳定处理的 JSON 错误结构
 */
@RestControllerAdvice
@Slf4j
public class ApiExceptionHandler {

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

    /** 登录密码错误或账号状态异常时，统一返回安全且稳定的 JSON 错误结构。 */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleStatus(ResponseStatusException exception) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        String message = exception.getReason() == null ? "请求失败" : exception.getReason();
        String code = status == HttpStatus.UNAUTHORIZED ? "AUTHENTICATION_FAILED" : "REQUEST_FAILED";
        return ResponseEntity.status(status).body(ApiError.of(code, message));
    }

    /** Sa-Token 拦截器产生未登录异常时返回 HTTP 401，供前端清理 token 并跳转登录页。 */
    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<ApiError> handleNotLogin() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of("NOT_LOGIN", "登录状态已失效，请重新登录"));
    }
}
