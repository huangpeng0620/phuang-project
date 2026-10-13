package com.sprouttrail.image.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 已生成并落盘的图片。
 */
@Getter
@AllArgsConstructor
public class GeneratedImage {

    /** 服务器保存时使用的安全文件名。 */
    private final String filename;

    /** 返回给浏览器的 PNG 字节。 */
    private final byte[] bytes;
}
