package com.sprouttrail.image.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

/**
 * 图片优化的表单请求，由 Spring MVC 绑定 multipart 中的图片和文字字段。
 */
@Getter
@Setter
@NoArgsConstructor
public class EditImageRequest {

    /** 本次编辑的原图；文件内容还需由服务层校验，不能信任扩展名和 MIME 声明。 */
    @NotNull(message = "请上传一张原图")
    private MultipartFile image;

    /** 用户确认的编辑要求，提交模型时保留全部字符。 */
    @NotBlank(message = "请输入图片编辑要求")
    @Size(max = 800, message = "图片编辑要求不能超过 800 个字符")
    private String prompt;

    /** 图片优化默认使用自动画幅，也允许用户选择现有固定比例。 */
    private ImageAspectRatio aspectRatio;

    public ImageAspectRatio effectiveAspectRatio() {
        return aspectRatio == null ? ImageAspectRatio.AUTO : aspectRatio;
    }
}
