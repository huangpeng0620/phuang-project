package com.sprouttrail.image.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 提示词优化接口的请求体。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OptimizePromptRequest {

    /** 用户输入的原始提示词；优化服务只读取它并提出追加建议，不会改写它。 */
    @NotBlank(message = "请输入图片描述")
    @Size(max = 800, message = "图片描述不能超过 800 个字符")
    private String prompt;
}
