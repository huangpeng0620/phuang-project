package com.sprouttrail.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 图片生成工作流中的统一业务异常，通过错误码和 HTTP 状态区分不同失败场景
 */
public class ImageWorkflowException extends RuntimeException {

    private final String code;
    private final HttpStatus status;
    private final String userMessage;

    /**
     * 创建统一业务异常。
     *
     * @param code        稳定的 API 错误码
     * @param status      HTTP 响应状态
     * @param userMessage 可安全返回给用户的提示
     */
    public ImageWorkflowException(String code, HttpStatus status, String userMessage) {
        this(code, status, userMessage, null);
    }

    /**
     * 创建包含底层原因的统一业务异常。
     *
     * @param code        稳定的 API 错误码
     * @param status      HTTP 响应状态
     * @param userMessage 可安全返回给用户的提示
     * @param cause       底层异常
     */
    public ImageWorkflowException(String code, HttpStatus status, String userMessage, Throwable cause) {
        super(userMessage, cause);
        this.code = code;
        this.status = status;
        this.userMessage = userMessage;
    }

    public String code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }

    public String userMessage() {
        return userMessage;
    }
}
