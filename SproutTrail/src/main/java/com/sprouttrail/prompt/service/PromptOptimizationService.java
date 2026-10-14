package com.sprouttrail.prompt.service;

import com.sprouttrail.prompt.model.response.OptimizePromptResponse;

/**
 * 定义提示词优化建议的业务操作。
 */
public interface PromptOptimizationService {

    /**
     * 根据用户的原始提示词生成三条可选择的补充建议，不直接改写原文。
     *
     * @param originalPrompt 用户输入的原始提示词
     * @return 三条经过校验的补充建议
     */
    OptimizePromptResponse optimize(String originalPrompt);
}
