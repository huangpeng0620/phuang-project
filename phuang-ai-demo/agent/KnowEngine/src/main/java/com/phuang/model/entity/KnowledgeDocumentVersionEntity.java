package com.phuang.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.phuang.model.enums.DocumentStatus;
import lombok.*;

/**
 * 文档版本实体
 *
 * @author huangpeng
 * @since 2026/8/29
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
@TableName("knowledge_document_version")
public class KnowledgeDocumentVersionEntity extends BaseEntity {

    /**
     * 版本ID
     */
    @TableId(type = IdType.AUTO)
    private Long versionId;

    /**
     * 关联文档ID
     */
    private Long docId;

    /**
     * 语义化版本号 (1.0.0)
     */
    @Builder.Default
    private String version = "1.0.0";

    /**
     * 该版本文档URL
     */
    private String docUrl;

    /**
     * 该版本转换后的文档URL
     */
    private String convertedDocUrl;

    /**
     * 该版本文档内容哈希值（SHA-256）
     */
    private String contentHash;

    /**
     * 版本状态：INIT, UPLOADED, CONVERTING, CONVERTED, CHUNKED, VECTOR_STORED
     */
    private DocumentStatus status;

    /**
     * 该版本上传用户
     */
    private String uploadUser;

    /**
     * 版本变更说明
     */
    private String changelog;
}
