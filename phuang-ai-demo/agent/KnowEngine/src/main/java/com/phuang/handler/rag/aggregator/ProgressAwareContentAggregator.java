package com.phuang.handler.rag.aggregator;

import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.fastjson2.JSON;
import com.phuang.model.entity.ChatMessageEntity;
import com.phuang.model.enums.RetrievalSource;
import com.phuang.service.chat.ChatMessageService;
import com.phuang.util.ReferenceUtil;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.aggregator.ContentAggregator;
import dev.langchain4j.rag.query.Query;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.phuang.model.constant.MetadataKeyConstant.CHUNK_ID;
import static com.phuang.model.constant.MetadataKeyConstant.DOC_ID;


/**
 * 带进度通知的内容聚合器
 * <p>
 * 在委托执行 {@link ContentAggregator#aggregate(Map)} 前后发送进度通知，
 * 用于流式返回前端当前处理阶段，减少用户等待焦虑。
 * <p>
 * 进度通知顺序：
 * <ol>
 *   <li>聚合前：{@code [PROGRESS]:正在排序筛选结果...}</li>
 *   <li>聚合后：{@code [PROGRESS]:正在生成回答...}（聚合完成后即将进入LLM生成阶段）</li>
 * </ol>
 *
 * @see ContentAggregator
 */
@Slf4j
public class ProgressAwareContentAggregator implements ContentAggregator {

    private final ContentAggregator delegate;

    private final Consumer<String> progressCallback;

    private final String assistantMessageId;

    private final ChatMessageService chatMessageService;

    @Builder
    public ProgressAwareContentAggregator(ContentAggregator delegate,
                                          Consumer<String> progressCallback,
                                          String assistantMessageId,
                                          ChatMessageService chatMessageService) {
        this.chatMessageService = chatMessageService;
        this.delegate = delegate;
        this.assistantMessageId = assistantMessageId;
        this.progressCallback = progressCallback;
    }

    @Override
    public List<Content> aggregate(Map<Query, Collection<List<Content>>> queryToContents) {
        // 重排序/聚合操作前发送进度
        if (Objects.nonNull(progressCallback)) {
            progressCallback.accept("[PROGRESS]:正在排序筛选结果...");
            log.info("[PROGRESS]:正在排序筛选结果...");
        }

        //交由 delegate 执行重排序/聚合
        List<Content> contentResults = delegate.aggregate(queryToContents);

        try {
            /**
             * 构造【文档】维度的RAG引用信息,用于前端展示
             *  文档列表需要通过 DOC_ID 进行去重
             */
            List<ChatMessageEntity.RagReference> ragReferencesDocs = contentResults.stream().collect(Collectors.toMap(
                            content -> content.textSegment().metadata().getInteger(DOC_ID),
                            content -> content,
                            (existing, replacement) -> existing)).values().stream()
                    .map(content -> ReferenceUtil.getRagReference(content, RetrievalSource.HYBRID))
                    .collect(Collectors.toList());

            // 过滤掉 chunkId 为空的引用，一般是非知识库检索得到的结果
            ragReferencesDocs = ragReferencesDocs.stream().filter(reference -> reference.getChunkId() != null).collect(Collectors.toList());

            if (Objects.nonNull(progressCallback) && CollectionUtil.isNotEmpty(ragReferencesDocs)) {
                progressCallback.accept("[REFERENCE]:" + JSON.toJSONString(ragReferencesDocs));
                log.info("[REFERENCE]:{}", JSON.toJSONString(ragReferencesDocs));
            }

            /**
             * 构造【CHUNK】维度的RAG引用信息,这份列表的定位是完整保存最终参与回答的 chunk 信息,用于数据库持久化
             * 文档列表需要通过 CHUNK_ID 进行去重
             * 持久化的目的是为了前端后续展示对话详情中的所引用的信息有哪些(注意:是持久化到模型返回的消息记录,而不是用户提问的消息记录)
             */
            List<ChatMessageEntity.RagReference> ragReferenceChunks = contentResults.stream().collect(Collectors.toMap(
                            content -> content.textSegment().metadata().getString(CHUNK_ID),
                            content -> content,
                            (existing, replacement) -> existing, LinkedHashMap::new)).values().stream()
                    .map(content -> ReferenceUtil.getRagReference(content, RetrievalSource.HYBRID))
                    .collect(Collectors.toList());

            if (CollectionUtil.isNotEmpty(ragReferenceChunks) && Objects.nonNull(chatMessageService) && Objects.nonNull(assistantMessageId)) {
                //更新对话引用信息
                chatMessageService.updateRagReferences(assistantMessageId, ragReferenceChunks);
            }
        } catch (Exception e) {
            log.warn("RAG引用信息回写失败: assistantMessageId:{}", assistantMessageId, e);
        }

        // 聚合完成后发送进度: 即将进入 LLM 生成
        if (Objects.nonNull(progressCallback)) {
            progressCallback.accept("[PROGRESS]:正在生成回答...");
            log.info("[PROGRESS]:正在生成回答...");
        }
        return contentResults;
    }
}
