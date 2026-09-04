package com.phuang.model.dto;

/**
 * MinerU 任务查询结果。
 *
 * @param taskId MinerU 任务 ID
 * @param state 状态：pending、running、converting、done、failed
 * @param markdownMinioUrl 解析完成后 Markdown 的 MinIO 地址
 * @param errorMessage 解析失败原因
 */
public record MinerUParseResult(String taskId,
                          String state,
                          String markdownMinioUrl,
                          String errorMessage) {
}