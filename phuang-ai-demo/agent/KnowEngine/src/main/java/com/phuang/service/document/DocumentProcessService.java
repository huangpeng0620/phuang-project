package com.phuang.service.document;

import com.phuang.model.dto.DocumentSplitParam;
import com.phuang.model.dto.DocumentUploadParam;
import com.phuang.model.entity.KnowledgeDocumentVersionEntity;
import org.springframework.web.multipart.MultipartFile;

/**
 *
 * @description 文档处理服务接口
 *  - 负责文档的业务流程处理：上传、转换、分段、向量化
 * @author huangpeng
 * @since 2026/8/30
 */
public interface DocumentProcessService {

    Boolean upload(DocumentUploadParam documentUploadParam, String uploadUser) throws Exception;

    Boolean uploadNewVersion(Long docId, String version, MultipartFile file, String uploadUser, String changelog) throws Exception;

    Boolean completeUploadedDocumentProcessing(Long documentId, Long documentVersionId) throws Exception;

    Integer split(DocumentSplitParam documentSplitParam);

    Boolean embedAndStore(KnowledgeDocumentVersionEntity documentVersion);

    void switchVersion(Long docId, Long versionId);

}
