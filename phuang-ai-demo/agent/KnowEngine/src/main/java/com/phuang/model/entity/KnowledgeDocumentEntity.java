package com.phuang.model.entity;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.phuang.model.dto.DocumentUploadParam;
import com.phuang.model.enums.DocumentStatus;
import com.phuang.model.enums.KnowledgeBaseType;
import com.phuang.model.enums.RoleEnum;
import com.phuang.util.DocumentPermissionUtils;
import lombok.*;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @description KnowledgeDocumentEntity
 * @author huangpeng
 * @since 2026/8/29
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
@TableName("knowledge_document")
public class KnowledgeDocumentEntity extends BaseEntity {
    /**
     * 文档ID
     */
    @TableId(type = IdType.AUTO)
    private Long docId;

    /**
     * 文档标题
     */
    private String docTitle;

    /**
     * 状态：INIT, UPLOADED, CONVERTING, CONVERTED, CHUNKED, VECTOR_STORED
     */
    private DocumentStatus status;

    /**
     * 可见范围
     */
    private String accessibleBy;

    /**
     * 文档描述
     */
    private String description;

    /**
     * 知识库类型
     */
    private KnowledgeBaseType knowledgeBaseType;

    /**
     * 扩展字段，保存JSON字符串
     */
    private String extension;

    /**
     * 当前激活版本ID，指向 knowledge_document_version.version_id
     */
    private Long currentVersionId;

    public KnowledgeDocumentEntity create(DocumentUploadParam documentUploadParam) {
        this.setDocTitle(documentUploadParam.title());
        this.setStatus(DocumentStatus.UPLOADED);
        this.setDescription(documentUploadParam.description());
        this.setKnowledgeBaseType(KnowledgeBaseType.valueOf(documentUploadParam.knowledgeBaseType()));
        this.setTableName(documentUploadParam.tableName());
        this.setAccessibleBy(DocumentPermissionUtils.getDocumentPermission(RoleEnum.valueOf(documentUploadParam.accessibleBy())));
        return this;
    }

    @JsonIgnore
    public void setTableName(String tableName) {
        Map<String, Serializable> extensionMap;
        if (extension == null) {
            extensionMap = new HashMap<String, Serializable>();
        } else {
            extensionMap = JSON.parseObject(extension, Map.class);
        }
        extensionMap.put("tableName", tableName);
        this.extension = JSON.toJSONString(extensionMap);
    }

    @JsonIgnore
    public String getTableName() {
        if (extension != null && !extension.isEmpty()) {
            return (String) JSON.parseObject(extension, Map.class).get("tableName");
        }
        return null;
    }
}
