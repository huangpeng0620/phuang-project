package com.phuang.handler.graph;

import com.alibaba.fastjson2.JSON;
import com.phuang.model.entity.KnowledgeDocumentEntity;
import com.phuang.model.entity.KnowledgeDocumentVersionEntity;
import com.phuang.model.entity.KnowledgeSegmentEntity;
import jakarta.annotation.Resource;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.TransactionContext;
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
     * 将一个分段及其已验证事实写入图谱。
     * <p>
     * 主流程按业务动作拆开：
     *      1.先登记【事实来自哪个分段】
     *      2.再登记【事实说的是谁】
     *      3.最后登记【事实关联了谁】和 【原文证据是什么】
     * </p>
     */
    public void writeSegment(KnowledgeSegmentEntity segment, List<GraphFactExtractor.Fact> facts) {
        if (facts.isEmpty()) {
            return;
        }
        Long versionId = segment.getDocumentVersion();
        try (Session session = driver.session()) {
            session.executeWrite(tx -> {
                saveChunkSource(tx, segment);
                for (GraphFactExtractor.Fact fact : facts) {
                    Map<String, Object> params = factParams(versionId, segment.getId(), fact);
                    saveFactSubject(tx, params);
                    saveFactObjectIfPresent(tx, fact, params);
                    saveFactEvidence(tx, params);
                }
                return null;
            });
        }
    }

    /**
     * 保存文档版本包含当前分段这条来源信息
     * <p>
     * 写入/更新的节点:
     * KGChunk 文档分段节点，字段包括:
     *      1. segmentId: MySQL knowledge_segment 表主键，用来回查原始分段正文
     *      2. docId: 文档 ID
     *      3. versionId: 文档版本 ID,用来隔离同一资料的不同版本
     *      4. chunkId: 业务分段 ID，保留原有分段标识
     *
     * 写入的关系: KGVersion -[:HAS_CHUNK]-> KGChunk，表示 【这个文档版本包含这个分段】
     * </p>
     */
    private static void saveChunkSource(TransactionContext tx, KnowledgeSegmentEntity segment) {
        tx.run("""
                MATCH (v:KGVersion {versionId: $versionId})
                MERGE (c:KGChunk {segmentId: $segmentId})
                SET
                    c.docId = $docId,
                    c.versionId = $versionId,
                    c.chunkId = $chunkId
                MERGE (v)-[:HAS_CHUNK]->(c)
                """, segmentParams(segment)).consume();
    }

    /**
     * 用于保存一张事实卡片和它的主体实体
     *
     * <p>
     * 写入/更新的节点:
     * KGFact 事实节点，字段包括:
     *        1.factId: 根据版本、主体、关系、客体/数值、单位和限定条件生成的稳定 ID
     *        2.versionId: 事实所属文档版本 ID，不同版本的相同事实不会互相覆盖
     *        3.predicate: 事实关系或数值指标，例如 USES_PART、HAS_FEATURE、RANGE_KM
     *        4.qualifiers: 年款、配置、地区、生效时间等限定条件的 JSON 字符串
     *        5.value: 数值事实的数值，例如续航 750；关系事实为 null
     *        6.unit: 数值事实单位，例如 KM、CNY、MONTH；关系事实为 null
     *
     *      KGFact 表示的是【一个有证据、有版本、有适用范围的事实卡片】,当客体实体为数值事实,构建时就不会存在客体实体,
     *  因为数值事实本质上也是事实，不是一个业务实体;
     *
     * KGEntity 实体节点，字段包括:
     *      1.entityKey: 实体唯一键，由实体类型和规范化名称组成。
     *      2.name: 实体展示名称，例如“车型 A”“电池 B”。
     *      3.type: 实体类型，例如 VEHICLE、PART、FEATURE、COMPANY。
     *
     * 写入的关系: KGFact -[:SUBJECT]-> KGEntity，表示【这条事实说的是哪个主体】
     * </p>
     */
    private static void saveFactSubject(TransactionContext tx, Map<String, Object> params) {
        tx.run("""
                MERGE (s:KGEntity {entityKey: $subjectKey})
                ON CREATE SET
                    s.name = $subjectName,
                    s.type = $subjectType
                MERGE (f:KGFact {factId: $factId})
                SET
                    f.versionId = $versionId,
                    f.predicate = $predicate,
                    f.qualifiers = $qualifiers,
                    f.value = $value,
                    f.unit = $unit
                MERGE (f)-[:SUBJECT]->(s)
                """, params).consume();
    }

    /**
     * 保存事实的客体实体，数值事实没有客体实体，例如 RANGE_KM=750，所以直接跳过。
     * <p>
     * KGEntity 客体实体节点，字段包括:
     *      1.entityKey: 客体实体唯一键
     *      2.name: 客体实体展示名称，例如: 电池 B、厂商 C、L2辅助驾驶
     *      3.type: 客体实体类型，例如 PART、COMPANY、FEATURE

     * 写入的关系: KGFact -[:OBJECT]-> KGEntity，表示【这条事实关联到哪个客体】
     * </p>
     */
    private static void saveFactObjectIfPresent(TransactionContext tx, GraphFactExtractor.Fact fact,
                                                Map<String, Object> params) {
        if (fact.getObject() == null) {
            return;
        }
        tx.run("""
                MATCH (f:KGFact {factId: $factId})
                MERGE (o:KGEntity {entityKey: $objectKey})
                ON CREATE SET
                    o.name = $objectName,
                    o.type = $objectType
                MERGE (f)-[:OBJECT]->(o)
                """, params).consume();
    }

    /**
     * 保存事实的原文证据
     * <p>
     * 写入的关系:
     * KGFact -[:SUPPORTED_BY]-> KGChunk，表示【这条事实由哪个文档分段支撑】
     * SUPPORTED_BY 关系上的 quote 字段保存原文证据片段，来自 GraphFactExtractor 校验过的 evidence。
     * </p>
     */
    private static void saveFactEvidence(TransactionContext tx, Map<String, Object> params) {
        tx.run("""
                MATCH (f:KGFact {factId: $factId}), (c:KGChunk {segmentId: $segmentId})
                MERGE (f)-[e:SUPPORTED_BY]->(c)
                SET e.quote = $evidence
                """, params).consume();
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
        return params;
    }

    /**
     * 事实 ID 不含分段 ID：同一版本多个分段支持同一事实时，复用事实节点并保留多条证据。
     * 版本 ID 则必须包含在内，以便版本切换和失败重建时互不覆盖。
     */
    private static Map<String, Object> factParams(Long versionId, Long segmentId, GraphFactExtractor.Fact fact) {
        String qualifiers = JSON.toJSONString(fact.getQualifiers());
        String objectKey = fact.getObject() == null ? "" : fact.getObject().key();
        String value = fact.getValue() == null ? "" : fact.getValue().stripTrailingZeros().toPlainString();
        String identity = versionId + "|" + fact.getSubject().key() + "|" + fact.getPredicate()
                + "|" + objectKey + "|" + value + "|" + fact.getUnit() + "|" + qualifiers;
        Map<String, Object> params = new HashMap<>();
        params.put("factId", sha256(identity));
        params.put("versionId", versionId);
        params.put("segmentId", segmentId);
        params.put("subjectKey", fact.getSubject().key());
        params.put("subjectName", fact.getSubject().getName());
        params.put("subjectType", fact.getSubject().getType());
        params.put("predicate", fact.getPredicate());
        params.put("objectKey", objectKey);
        params.put("objectName", fact.getObject() == null ? null : fact.getObject().getName());
        params.put("objectType", fact.getObject() == null ? null : fact.getObject().getType());
        params.put("value", fact.getValue() == null ? null : fact.getValue().doubleValue());
        params.put("unit", fact.getUnit());
        params.put("qualifiers", qualifiers);
        params.put("evidence", fact.getEvidence());
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
