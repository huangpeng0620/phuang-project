package com.sprouttrail.image.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 文生图接口的请求体
 *
 * @param prompt 用户对目标画面的文字描述
 */
public record GenerateImageRequest(
        @NotBlank(message = "请输入图片描述")
        @Size(max = 800, message = "图片描述不能超过 800 个字符")
        String prompt) {
}