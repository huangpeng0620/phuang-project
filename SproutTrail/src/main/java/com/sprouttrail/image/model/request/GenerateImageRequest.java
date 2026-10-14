package com.sprouttrail.image.model.request;

import com.sprouttrail.image.model.enums.ImageAspectRatio;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 文生图接口的请求体
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GenerateImageRequest {

    @NotBlank(message = "请输入图片描述")
    @Size(max = 800, message = "原始图片描述")
    private String prompt;

    @Size(max = 1200, message = "提示词补充内容")
    private String supplement;

    /** 用户选择的图片画幅；未传时兼容旧请求并使用 1:1。 */
    private ImageAspectRatio aspectRatio;

    /**
     * 组合图片模型实际使用的提示词。原始提示词作为不可变前缀保留，
     * 只有用户主动选择并确认的补充内容才会追加到末尾。
     *
     * @return 原始提示词，或原始提示词加上用户确认的补充内容
     */
    public String effectivePrompt() {
        if (supplement == null || supplement.isBlank()) {
            return prompt;
        }
        return prompt + "\n\n" + supplement;
    }

    /**
     * 获取本次生成使用的画幅。
     *
     * <p>旧版前端不会发送该字段，因此空值继续按原来的 1:1 尺寸处理。
     *
     * @return 用户选择的画幅，或默认的 1:1 画幅
     */
    public ImageAspectRatio effectiveAspectRatio() {
        return aspectRatio == null ? ImageAspectRatio.SQUARE : aspectRatio;
    }
}
