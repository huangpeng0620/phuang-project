package com.sprouttrail.image.model.response;

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

    /** 上传成功后由 MinIO 服务返回的图片地址。 */
    private final String minioUrl;
}
