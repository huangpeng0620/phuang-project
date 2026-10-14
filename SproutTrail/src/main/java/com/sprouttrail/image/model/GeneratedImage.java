package com.sprouttrail.image.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 已生成并上传 MinIO 的图片。
 */
@Getter
@AllArgsConstructor
public class GeneratedImage {

    /** 上传到 MinIO 时使用的对象名。 */
    private final String filename;

    /** 返回给浏览器用于预览和下载的 PNG 字节。 */
    private final byte[] bytes;
}
