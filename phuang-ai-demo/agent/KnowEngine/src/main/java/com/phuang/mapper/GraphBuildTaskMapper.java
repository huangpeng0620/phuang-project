package com.phuang.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 图谱构建任务的原子领取、状态回写及历史版本发现。
 * 领取采用单条条件 UPDATE，避免事件监听器和补偿任务同时处理同一版本。
 */
@Mapper
public interface GraphBuildTaskMapper {

    /** 只为尚未登记的版本创建任务；重复事件不会重置成功任务。 */
    @Insert("""
            INSERT IGNORE INTO graph_build_task (document_id, version_id, status)
            VALUES (#{documentId}, #{versionId}, 'PENDING')
            """)
    int insertPendingTaskIfAbsent(@Param("documentId") Long documentId, @Param("versionId") Long versionId);

    /**
     * 只领取待处理任务；失败或运行中任务不会自动重试。
     * 单条条件 UPDATE 防止事件监听器和补偿任务同时处理同一版本。
     */
    @Update("""
            UPDATE graph_build_task
            SET status = 'RUNNING', last_error = NULL, updated_at = NOW()
            WHERE version_id = #{versionId} AND status = 'PENDING'
            """)
    int updatePendingTaskToRunning(@Param("versionId") Long versionId);

    /** Neo4j 版本标记就绪后，将 MySQL 任务标记成功。 */
    @Update("""
            UPDATE graph_build_task SET status = 'SUCCEEDED', last_error = NULL, updated_at = NOW()
            WHERE version_id = #{versionId} AND status = 'RUNNING'
            """)
    int updateRunningTaskToSucceeded(@Param("versionId") Long versionId);

    /** 记录失败的异常类型；失败任务需人工处理后再执行。 */
    @Update("""
            UPDATE graph_build_task SET status = 'FAILED', last_error = #{lastError}, updated_at = NOW()
            WHERE version_id = #{versionId} AND status = 'RUNNING'
            """)
    int updateRunningTaskToFailed(@Param("versionId") Long versionId, @Param("lastError") String lastError);

    /** 返回一批尚未开始的任务，供 XXL-Job 补偿未执行的切分事件。 */
    @Select("""
            SELECT version_id FROM graph_build_task
            WHERE status = 'PENDING'
            ORDER BY updated_at ASC LIMIT #{limit}
            """)
    List<Long> selectPendingVersionIds(@Param("limit") int limit);

    /** 查找已有正文分段、但还没有构图任务的当前文档版本，用于历史回填。 */
    @Select("""
            SELECT v.version_id
            FROM knowledge_document_version v
            JOIN knowledge_document d ON d.doc_id = v.doc_id AND d.deleted = 0
            WHERE d.knowledge_base_type = 'DOCUMENT_SEARCH'
              AND d.current_version_id = v.version_id
              AND v.deleted = 0 AND v.status IN ('CHUNKED', 'VECTOR_STORED')
              AND EXISTS (SELECT 1 FROM knowledge_segment s
                          WHERE s.document_version = v.version_id AND s.deleted = 0)
              AND NOT EXISTS (SELECT 1 FROM graph_build_task t WHERE t.version_id = v.version_id)
            ORDER BY v.version_id LIMIT #{limit}
            """)
    List<Long> selectCurrentSegmentedDocumentSearchVersionIdsWithoutTask(@Param("limit") int limit);
}
