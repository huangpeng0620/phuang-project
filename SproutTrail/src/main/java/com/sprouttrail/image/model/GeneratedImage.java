package com.sprouttrail.image.model;

/**
 * 已生成并落盘的图片。
 *
 * @param filename 服务器保存时使用的安全文件名
 * @param bytes    返回给浏览器的 PNG 字节
 */
public record GeneratedImage(String filename, byte[] bytes) {
}
