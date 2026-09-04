package com.phuang.model.exception;

import java.io.Serial;

/**
 * 项目统一业务异常。
 * <p>
 * 用于表示可预期的业务失败，例如业务规则校验失败、外部服务调用失败等。
 * 上层可通过 {@link #getCode()} 获取错误码并转换为统一接口响应。
 */
public class BusinessException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String DEFAULT_ERROR_CODE = "BUSINESS_ERROR";

    private final String code;

    public BusinessException(String message) {
        this(DEFAULT_ERROR_CODE, message, null);
    }

    public BusinessException(String message, Throwable cause) {
        this(DEFAULT_ERROR_CODE, message, cause);
    }

    public BusinessException(String code, String message) {
        this(code, message, null);
    }

    public BusinessException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code == null || code.isBlank() ? DEFAULT_ERROR_CODE : code;
    }

    public String getCode() {
        return code;
    }
}
