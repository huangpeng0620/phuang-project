package com.sprouttrail.image.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * 提示词优化结果。
 */
@Getter
@AllArgsConstructor
public class OptimizePromptResponse {

    /** 三条供用户选择的追加建议。 */
    private final List<PromptSuggestion> suggestions;
}
