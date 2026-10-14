package com.sprouttrail.image.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    /** 用户名密码登录请求。 */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class LoginRequest {

        /** 登录用户名。 */
        @NotBlank(message = "请输入用户名")
        @Size(max = 64, message = "用户名不能超过 64 个字符")
        private String username;

        /** 登录密码；密码不会在日志或响应中回显。 */
        @NotBlank(message = "请输入密码")
        @Size(max = 72, message = "密码不能超过 72 个字符")
        private String password;
    }
}
