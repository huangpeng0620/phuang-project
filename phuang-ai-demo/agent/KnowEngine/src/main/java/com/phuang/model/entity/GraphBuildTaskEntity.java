package com.phuang.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 图谱构建任务，对应 MySQL 的 graph_build_task 表。
 * 每个文档版本最多一条任务，用于记录异步构图的进度和失败类型。
 * 此表没有 BaseEntity 的 deleted、lockVersion 字段，因此不继承 BaseEntity。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("graph_build_task")
public class GraphBuildTaskEntity {

    /** 数据库自增主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联的知识文档 ID，对应 knowledge_document.doc_id。 */
    private Long documentId;

    /** 关联的文档版本 ID；数据库唯一键保证一个版本只有一条构图任务。 */
    private Long versionId;

    /** 构图状态：PENDING、RUNNING、SUCCEEDED 或 FAILED。 */
    private String status;

    /** 最近一次失败的异常类型，不保存文档正文。 */
    private String lastError;

    /** 任务创建时间，由数据库默认值写入。 */
    private LocalDateTime createdAt;

    /** 最近一次状态变更或处理进度时间，由数据库自动更新。 */
    private LocalDateTime updatedAt;
}
