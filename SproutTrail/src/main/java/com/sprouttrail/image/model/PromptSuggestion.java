package com.sprouttrail.image.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 一条可追加到原始提示词末尾的提示词优化建议。
 */
@Getter
@AllArgsConstructor
public class PromptSuggestion {

    /** 方便用户区分方向的简短标题。 */
    private final String title;

    /** 追加描述，明确保留原文关键名词和关系且不改变原意。 */
    private final String supplement;
}
