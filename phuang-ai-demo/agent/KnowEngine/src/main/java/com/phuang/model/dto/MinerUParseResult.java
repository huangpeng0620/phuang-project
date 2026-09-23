package com.phuang.model.dto;

/**
 * MinerU 任务查询结果。
 *
 * @param taskId MinerU 任务 ID
 * @param state 状态：pending、running、converting、done、failed
 * @param resultFileUrl MinerU 任务结果文件地址
 * @param errorMessage 解析失败原因
 */
public record MinerUParseResult(String taskId,
                          String state,
                          String resultFileUrl,
                          String errorMessage) {
}
