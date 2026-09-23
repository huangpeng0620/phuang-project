package com.phuang.service.document.impl;

import cn.hutool.core.util.StrUtil;
import com.phuang.model.dto.MinerUParseResult;
import com.phuang.model.entity.KnowledgeDocumentEntity;
import com.phuang.model.enums.DocumentStatus;
import com.phuang.model.exception.BusinessException;
import com.phuang.service.FileProcessService;
import com.phuang.service.KnowledgeDocumentService;
import com.phuang.util.MinerUParseUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;

/**
 * 文件处理服务 - 负责文档转换处理
 */
@Service
@Slf4j
public abstract class MinerUProcessBaseServiceImpl implements FileProcessService {

    @Resource
    private KnowledgeDocumentService knowledgeDocumentService;

    @Resource
    private MinerUParseUtil minerUParseUtil;

    /**
     * 处理文档转换:  PDF --> Markdown 格式
     * 1. 从 MinIO 下载文件
     * 2. 调用文档解析接口获取md/zip
     * 3. 转换后的文档保存在minio上
     * 3. 更新文档状态和转换后的 URL
     *
     * @param document 文档对象
     */
    public String processDocument(KnowledgeDocumentEntity document, String fileMinioUrl, InputStream inputStream) {
        log.info("开始处理文档转换为 Markdown,documentId:{}", document.getDocTitle());
        try {
            //更新文档状态至【转换中】
            knowledgeDocumentService.advanceDocumentAndVersionStatus(document.getDocId(), document.getCurrentVersionId(), DocumentStatus.CONVERTING);

            // 发起 MinerU 解析任务并等待任务完成
            MinerUParseResult parseResult = waitForParseResult(fileMinioUrl);

            // 处理 MinerU 结果文件，生成最终 Markdown URL
            String markdownMinioUrl = minerUParseUtil.processParseResult(parseResult.taskId(), parseResult.resultFileUrl());

            //更新文档状态至【转换完成】
            knowledgeDocumentService.advanceDocumentAndVersionStatus(document.getDocId(), document.getCurrentVersionId(), DocumentStatus.CONVERTED);
            log.info("PDF 文档转换完成,documentId: {}", document.getDocTitle());
            return markdownMinioUrl;
        } catch (Exception e) {
            log.error("PDF 文档转换失败,documentId: {}", document.getDocTitle(), e);
            // 保留版本当前状态，由上传补偿任务重试；避免失败时提前写入新的 currentVersionId。
            throw new RuntimeException("PDF 文档转换失败: " + e.getMessage(), e);
        }
    }

    /**
     * 调用 MinerU 发起解析任务并等待任务完成
     * @param minioFileUrl minio 文件url
     * @return MinerU 任务查询结果
     * @throws Exception
     */
    private MinerUParseResult waitForParseResult(String minioFileUrl) throws Exception {
        String taskId = minerUParseUtil.createParseTask(minioFileUrl);
        if (StrUtil.isEmpty(taskId)) {
            throw new BusinessException("Miner发起解析任务失败,minioFileUrl:{}", minioFileUrl);
        }
        Thread.sleep(5000);
        for (int i = 0; i < Integer.MAX_VALUE; i++) {
            MinerUParseResult result = minerUParseUtil.queryParseResult(taskId);
            switch (result.state()) {
                case "done":
                    return result;
                case "failed":
                    throw new BusinessException("MinerU 解析失败:" + result.errorMessage());
                case "pending", "running", "converting":
                    Thread.sleep(3000);
                    continue;
                default:
                    throw new BusinessException("未知的 MinerU 任务状态:" + result.state());
            }
        }
        throw new BusinessException("MinerU 解析任务等待超时,taskId=" + taskId);
    }

}
