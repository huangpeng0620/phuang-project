package com.phuang.handler.graph;

import com.alibaba.fastjson2.JSON;
import com.phuang.model.entity.KnowledgeDocumentEntity;
import com.phuang.model.entity.KnowledgeDocumentVersionEntity;
import com.phuang.model.entity.KnowledgeSegmentEntity;
import jakarta.annotation.Resource;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * 使用项目已有 Neo4j Driver 写入带文档来源的知识图谱。
 * 所有标签和关系类型固定在代码中，模型输出只能作为参数值，不能拼接为 Cypher。
 */
@Component
public class Neo4jKnowledgeGraphWriter {

    /** 图谱节点唯一约束；应用首次构图时按需初始化，不要求增加另一个迁移框架。 */
    private static final List<String> CONSTRAINTS = List.of(
            "CREATE CONSTRAINT kg_document_id IF NOT EXISTS FOR (n:KGDocument) REQUIRE n.docId IS UNIQUE",
            "CREATE CONSTRAINT kg_version_id IF NOT EXISTS FOR (n:KGVersion) REQUIRE n.versionId IS UNIQUE",
            "CREATE CONSTRAINT kg_chunk_id IF NOT EXISTS FOR (n:KGChunk) REQUIRE n.segmentId IS UNIQUE",
            "CREATE CONSTRAINT kg_entity_key IF NOT EXISTS FOR (n:KGEntity) REQUIRE n.entityKey IS UNIQUE",
            "CREATE CONSTRAINT kg_fact_id IF NOT EXISTS FOR (n:KGFact) REQUIRE n.factId IS UNIQUE");

    /** Neo4j 连接由现有配置类统一创建并在应用关闭时释放。 */
    @Resource
    private Driver driver;

    /**
     * 开始构建一个文档版本。先把该版本标记为 BUILDING，并清理上次中断留下的事实和片段。
     * 共享实体不会删除；事实只在 READY 后被视为完整版本。
     */
    public void startVersion(KnowledgeDocumentEntity document, KnowledgeDocumentVersionEntity version) {
        ensureConstraints();
        Map<String, Object> params = new HashMap<>();
        params.put("docId", document.getDocId());
        params.put("title", document.getDocTitle());
        params.put("accessibleBy", document.getAccessibleBy());
        params.put("versionId", version.getVersionId());
        params.put("version", version.getVersion());
        try (Session session = driver.session()) {
            session.executeWrite(tx -> {
                tx.run("""
                        MERGE (d:KGDocument {docId: $docId})
                        SET d.title = $title, d.accessibleBy = $accessibleBy
                        MERGE (v:KGVersion {versionId: $versionId})
                        SET v.docId = $docId, v.version = $version, v.state = 'BUILDING'
                        MERGE (d)-[:HAS_VERSION]->(v)
                        """, params).consume();
                // 重试从头抽取时先清除该版本的旧结果，避免两次模型输出不同时遗留旧事实。
                tx.run("MATCH (f:KGFact {versionId: $versionId}) DETACH DELETE f", params).consume();
                tx.run("MATCH (c:KGChunk {versionId: $versionId}) DETACH DELETE c", params).consume();
                return null;
            });
        }
    }

    /**
     * 将一个分段及其已验证事实写入图谱。每个分段单独事务，失败时整个版本可安全重试。
     * 没有事实的分段不创建图节点，减少图谱存储和无意义遍历。
     */
    public void writeSegment(KnowledgeSegmentEntity segment, List<GraphFactExtractor.Fact> facts) {
        if (facts.isEmpty()) {
            return;
        }
        Long versionId = segment.getDocumentVersion();
        try (Session session = driver.session()) {
            session.executeWrite(tx -> {
                tx.run("""
                        MATCH (v:KGVersion {versionId: $versionId})
                        MERGE (c:KGChunk {segmentId: $segmentId})
                        SET c.docId = $docId, c.versionId = $versionId,
                            c.chunkId = $chunkId, c.chunkOrder = $chunkOrder
                        MERGE (v)-[:HAS_CHUNK]->(c)
                        """, segmentParams(segment)).consume();
                for (GraphFactExtractor.Fact fact : facts) {
                    Map<String, Object> params = factParams(versionId, segment.getId(), fact);
                    // 先合并共享实体，再合并逐版本事实；同一事实在多个分段出现时只增加证据边。
                    tx.run("""
                            MERGE (s:KGEntity {entityKey: $subjectKey})
                            ON CREATE SET s.name = $subjectName, s.type = $subjectType
                            MERGE (f:KGFact {factId: $factId})
                            SET f.versionId = $versionId, f.predicate = $predicate,
                                f.qualifiers = $qualifiers, f.value = $value, f.unit = $unit
                            MERGE (f)-[:SUBJECT]->(s)
                            """, params).consume();
                    if (fact.object() != null) {
                        tx.run("""
                                MATCH (f:KGFact {factId: $factId})
                                MERGE (o:KGEntity {entityKey: $objectKey})
                                ON CREATE SET o.name = $objectName, o.type = $objectType
                                MERGE (f)-[:OBJECT]->(o)
                                """, params).consume();
                    }
                    tx.run("""
                            MATCH (f:KGFact {factId: $factId}), (c:KGChunk {segmentId: $segmentId})
                            MERGE (f)-[e:SUPPORTED_BY]->(c)
                            SET e.quote = $evidence
                            """, params).consume();
                }
                return null;
            });
        }
    }

    /**
     * 所有分段写入成功后，把整个版本标记为可用
     * @param versionId
     */
    public void completeVersion(Long versionId) {
        try (Session session = driver.session()) {
            session.executeWrite(tx -> {
                tx.run("""
                        MATCH (v:KGVersion {versionId: $versionId})
                        SET v.state = 'READY', v.completedAt = datetime()
                        """, Map.of("versionId", versionId)).consume();
                return null;
            });
        }
    }

    /**
     * 首次使用时创建唯一约束；Neo4j 的 IF NOT EXISTS 使并发初始化也具备幂等性
     */
    private void ensureConstraints() {
        try (Session session = driver.session()) {
            for (String constraint : CONSTRAINTS) {
                session.executeWrite(tx -> {
                    tx.run(constraint).consume();
                    return null;
                });
            }
        }
    }

    /**
     * 从 MySQL 分段主键和版本信息构建固定的 Cypher 参数
     * @param segment
     * @return
     */
    private static Map<String, Object> segmentParams(KnowledgeSegmentEntity segment) {
        Map<String, Object> params = new HashMap<>();
        params.put("segmentId", segment.getId());
        params.put("docId", segment.getDocumentId());
        params.put("versionId", segment.getDocumentVersion());
        params.put("chunkId", segment.getChunkId());
        params.put("chunkOrder", segment.getChunkOrder());
        return params;
    }

    /**
     * 事实 ID 不含分段 ID：同一版本多个分段支持同一事实时，复用事实节点并保留多条证据。
     * 版本 ID 则必须包含在内，以便版本切换和失败重建时互不覆盖。
     */
    private static Map<String, Object> factParams(Long versionId, Long segmentId, GraphFactExtractor.Fact fact) {
        String qualifiers = JSON.toJSONString(fact.qualifiers());
        String objectKey = fact.object() == null ? "" : fact.object().key();
        String value = fact.value() == null ? "" : fact.value().stripTrailingZeros().toPlainString();
        String identity = versionId + "|" + fact.subject().key() + "|" + fact.predicate()
                + "|" + objectKey + "|" + value + "|" + fact.unit() + "|" + qualifiers;
        Map<String, Object> params = new HashMap<>();
        params.put("factId", sha256(identity));
        params.put("versionId", versionId);
        params.put("segmentId", segmentId);
        params.put("subjectKey", fact.subject().key());
        params.put("subjectName", fact.subject().name());
        params.put("subjectType", fact.subject().type());
        params.put("predicate", fact.predicate());
        params.put("objectKey", objectKey);
        params.put("objectName", fact.object() == null ? null : fact.object().name());
        params.put("objectType", fact.object() == null ? null : fact.object().type());
        params.put("value", fact.value() == null ? null : fact.value().doubleValue());
        params.put("unit", fact.unit());
        params.put("qualifiers", qualifiers);
        params.put("evidence", fact.evidence());
        return params;
    }

    /**
     * 使用 JDK 自带的 SHA-256 生成稳定的事实标识
     * @param text
     * @return
     */
    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
