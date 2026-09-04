package com.phuang.service.document;

import cn.hutool.core.util.StrUtil;
import com.phuang.model.dto.MinerUParseResult;
import com.phuang.model.entity.KnowledgeDocumentEntity;
import com.phuang.model.enums.DocumentStatus;
import com.phuang.model.exception.BusinessException;
import com.phuang.service.FileProcessService;
import com.phuang.service.KnowledgeDocumentService;
import com.phuang.util.MineruParseUtilCopy;
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
    private MineruParseUtilCopy mineruParseUtilCopy;

    /**
     * 处理文档转换:  PDF --> Markdown 格式
     * 1. 从 MinIO 下载文件
     * 2. 调用文档解析接口获取md/zip
     * 3. 转换后的文档保存在minio上
     * 3. 更新文档状态和转换后的 URL
     *
     * @param document 文档对象
     */
    public String processDocument(KnowledgeDocumentEntity document, String fileMinioUrl, InputStream inputStream) throws Exception {
        log.info("开始处理文档转换为 Markdown,documentId:{}", document.getDocTitle());
        try {
            //更新文档状态至【转换中】
            knowledgeDocumentService.advanceDocumentAndVersionStatus(document.getDocId(), document.getCurrentVersionId(), DocumentStatus.CONVERTING);

            //转换并获取文档 url
            String markdownMinioUrl = parseDocumentToMarkdown(fileMinioUrl);

            //更新文档状态至【转换完成】
            knowledgeDocumentService.advanceDocumentAndVersionStatus(document.getDocId(), document.getCurrentVersionId(), DocumentStatus.CONVERTED);
            log.info("PDF 文档转换完成,documentId: {}", document.getDocTitle());
            return markdownMinioUrl;
        } catch (Exception e) {
            log.error("PDF 文档转换失败,documentId: {}", document.getDocTitle(), e);
            // 转换失败: 状态回滚为 UPLOADED
            document.setStatus(DocumentStatus.UPLOADED);
            knowledgeDocumentService.updateById(document);
            throw new RuntimeException("PDF 文档转换失败: " + e.getMessage(), e);
        }
    }

    /**
     * 调用 Miner 进行任务解析
     * @param minioFileUrl minio 文件url
     * @return Miner的md文件url
     * @throws Exception
     */
    private String parseDocumentToMarkdown(String minioFileUrl) throws Exception {
        String taskId = mineruParseUtilCopy.createParseTask(minioFileUrl);
        if (StrUtil.isEmpty(taskId)) {
            throw new BusinessException("Miner发起解析任务失败,minioFileUrl:{}", minioFileUrl);
        }
        Thread.sleep(5000);
        for (int i = 0; i < Integer.MAX_VALUE; i++) {
            MinerUParseResult result = mineruParseUtilCopy.queryParseResult(taskId);
            switch (result.state()) {
                case "done":
                    return result.markdownMinioUrl();
                case "failed":
                    throw new BusinessException("MinerU 解析失败:" + result.errorMessage());
                case "pending", "running", "converting":
                    Thread.sleep(3000);
                default:
                    throw new BusinessException("未知的 MinerU 任务状态:" + result.state());
            }
        }
        throw new BusinessException("MinerU 解析任务等待超时,taskId=");
    }

}
