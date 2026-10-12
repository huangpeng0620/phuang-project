package com.sprouttrail.image.exception;

/**
 * 图片已经生成，但写入服务器本地目录失败时抛出的业务异常。
 */
public class ImageStorageException extends RuntimeException {

    /**
     * 创建图片存储异常。
     *
     * @param message 面向日志的异常说明
     * @param cause   文件系统抛出的原始异常
     */
    public ImageStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
