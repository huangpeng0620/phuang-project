package com.sprouttrail.common.model.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

/**
 * API 统一错误响应。
 */
@Getter
@AllArgsConstructor
public class ApiError {

    /** 错误发生时间。 */
    private final Instant timestamp;

    /** 供前端或日志检索使用的稳定错误码。 */
    private final String code;

    /** 可以直接展示给用户的错误说明。 */
    private final String message;

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
