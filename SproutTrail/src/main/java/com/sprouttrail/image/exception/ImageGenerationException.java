package com.sprouttrail.image.exception;

/**
 * 调用图片模型失败或模型返回无效内容时抛出的业务异常
 */
public class ImageGenerationException extends RuntimeException {

    /**
     * 创建图片生成异常
     *
     * @param message 面向日志的异常说明
     */
    public ImageGenerationException(String message) {
        super(message);
    }

    /**
     * 创建包含底层原因的图片生成异常
     *
     * @param message 面向日志的异常说明
     * @param cause   模型 SDK 抛出的原始异常
     */
    public ImageGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
