package com.sprouttrail.image.model;

import java.time.Instant;

/**
 * API 统一错误响应。
 *
 * @param timestamp 错误发生时间
 * @param code      供前端或日志检索使用的稳定错误码
 * @param message   可以直接展示给用户的错误说明
 */
public record ApiError(Instant timestamp, String code, String message) {

    /**
     * 使用当前时间创建错误响应。
     *
     * @param code    稳定错误码
     * @param message 用户可读信息
     * @return 标准错误响应
     */
    public static ApiError of(String code, String message) {
        return new ApiError(Instant.now(), code, message);
    }
}
