package com.sprouttrail.image.controller;

import com.sprouttrail.image.model.OptimizePromptRequest;
import com.sprouttrail.image.model.OptimizePromptResponse;
import com.sprouttrail.image.service.PromptOptimizationService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供只生成文字建议的提示词优化接口
 */
@RestController
@RequestMapping("/api/prompts")
public class PromptController {

    @Resource
    private PromptOptimizationService promptOptimizationService;

    /**
     * 基于原始提示词返回三条追加建议
     *
     * @param request 用户原始提示词
     * @return 三条文字建议
     */
    @PostMapping(value = "/optimize", consumes = MediaType.APPLICATION_JSON_VALUE)
    public OptimizePromptResponse optimize(@Valid @RequestBody OptimizePromptRequest request) {
        return promptOptimizationService.optimize(request.getPrompt());
    }
}
