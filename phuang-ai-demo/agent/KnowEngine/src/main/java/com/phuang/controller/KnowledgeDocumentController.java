package com.phuang.controller;

import com.phuang.model.dto.DocumentSplitParam;
import com.phuang.model.dto.DocumentUploadParam;
import com.phuang.service.KnowledgeDocumentService;
import com.phuang.service.document.DocumentProcessService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 *
 * @description 知识文档控制器
 * @author huangpeng
 * @since 2026/8/29
 */
@RestController
@RequestMapping("/api/document")
public class KnowledgeDocumentController {

    @Resource
    private DocumentProcessService documentProcessService;

    @Resource
    private KnowledgeDocumentService knowledgeDocumentService;

    /**
     * 文件上传接口
     * @param uploadUser 上传人(后期替换成token获取 todo)
     * @param file 上传的文件
     * @param title 文件标题
     * @param version 文档版本
     * @param tableName 表名称(文件类型为excel时必填)
     * @param description 文件描述
     * @param knowledgeBaseType 文件类型
     * @param accessibleBy 可见范围
     * @return
     * @throws Exception
     */
    @PostMapping("/upload")
    public Boolean uploadFile(@RequestParam("uploadUser") String uploadUser,
                              @RequestParam("file") MultipartFile file,
                              @RequestParam("title") String title,
                              @RequestParam(value = "version", required = false, defaultValue = "1.0.0") String version,
                              @RequestParam(value = "tableName", required = false) String tableName,
                              @RequestParam("description") String description,
                              @RequestParam("knowledgeBaseType") String knowledgeBaseType,
                              @RequestParam(value = "accessibleBy", required = false) String accessibleBy) throws Exception {
        return documentProcessService.upload(new DocumentUploadParam(file, title, accessibleBy, description, knowledgeBaseType, tableName, version), uploadUser);
    }

    /**
     * 上传文档新版本
     *
     * @param file        新版本文件
     * @param docId       文档ID（knowledge_document.doc_id）
     * @param version     新版本号（语义化版本，如 "2.0.0"，必须大于现有最新版本号）
     * @param changelog   版本变更说明（可选）
     * @return 更新后的文档记录
     */
    @PostMapping("/upload-version")
    public Boolean uploadVersion(
            @RequestParam("file") MultipartFile file,
            @RequestParam("uploadUser") String uploadUser,
            @RequestParam("docId") Long docId,
            @RequestParam("version") String version,
            @RequestParam(value = "changelog", required = false) String changelog) throws Exception {
        return documentProcessService.uploadNewVersion(docId, version, file, uploadUser, changelog);
    }

    /**
     * 对文档进行切分
     * 注意：此方法为手动触发切分接口，正常流程由事件驱动自动执行
     *
     * @param documentId 文档ID
     * @return 切分后的片段数量
     */
    @PostMapping("/split/{documentId}")
    public Integer splitDocument(@PathVariable("documentId") Long documentId,
                                 @RequestParam("splitType") String splitType,
                                 @RequestParam("chunkSize") Integer chunkSize,
                                 @RequestParam(value = "overlap", required = false) Integer overlap,
                                 @RequestParam(value = "regex", required = false) String regex,
                                 @RequestParam(value = "titleLevel", required = false) Integer titleLevel,
                                 @RequestParam(value = "separator", required = false) String separator) {
        return documentProcessService.split(new DocumentSplitParam(documentId, splitType, chunkSize, overlap, titleLevel, separator, regex));
    }

    /**
     * 让指定版本生效（重新向量化）：对 STORED 分段重新 embed 写入 ES，版本状态升为 VECTOR_STORED
     *
     * @param versionId 版本ID（knowledge_document_version.version_id）
     */
    @PostMapping("/activate-version")
    public void activateVersion(@RequestParam("versionId") Long versionId) {
        knowledgeDocumentService.activateVersion(versionId);
    }

    /**
     * 切换文档到指定版本
     * 清理当前版本的分段和向量，恢复目标版本的文件URL和状态，状态置为 CONVERTED 等待重新切片
     *
     * @param docId     文档ID
     * @param versionId 目标版本ID
     * @return 更新后的文档记录
     */
    @PostMapping("/switch-version")
    public void switchVersion(@RequestParam("docId") Long docId,
                              @RequestParam("versionId") Long versionId) {
        documentProcessService.switchVersion(docId, versionId);
    }

}
