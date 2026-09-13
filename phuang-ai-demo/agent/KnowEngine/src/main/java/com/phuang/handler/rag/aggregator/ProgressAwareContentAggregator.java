package com.phuang.handler.rag.aggregator;

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
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    private final String chatMessageId;

    private final ChatMessageService chatMessageService;

    @Builder
    public ProgressAwareContentAggregator(ContentAggregator delegate, Consumer<String> progressCallback, String chatMessageId, ChatMessageService chatMessageService) {
        this.chatMessageService = chatMessageService;
        this.delegate = delegate;
        this.chatMessageId = chatMessageId;
        this.progressCallback = progressCallback;
    }

    @Override
    public List<Content> aggregate(Map<Query, Collection<List<Content>>> queryToContents) {
        // 发送进度：开始重排序/聚合
        if (progressCallback != null) {
            progressCallback.accept("[PROGRESS]:正在排序筛选结果...");
            log.info("[PROGRESS]:正在排序筛选结果...");
        }

        List<Content> results = delegate.aggregate(queryToContents);

        try {
            // 文档维度的RAG引用信息，用于前端展示
            List<ChatMessageEntity.RagReference> ragReferencesDocs = results.stream()
                    .collect(Collectors.toMap(
                            content -> content.textSegment().metadata().getInteger(DOC_ID),
                            content -> content,
                            (existing, replacement) -> existing
                    )).values().stream()
                    .map(content -> ReferenceUtil.getRagReference(content, RetrievalSource.HYBRID))
                    .collect(Collectors.toList());

            // chunk维度的RAG引用信息，用于数据持久化
            List<ChatMessageEntity.RagReference> ragReferenceChunks = results.stream()
                    .collect(Collectors.toMap(
                            content -> content.textSegment().metadata().getString(CHUNK_ID),
                            content -> content,
                            (existing, replacement) -> existing,
                            LinkedHashMap::new
                    )).values().stream()
                    .map(content -> ReferenceUtil.getRagReference(content, RetrievalSource.HYBRID))
                    .collect(Collectors.toList());

            if (!CollectionUtils.isEmpty(ragReferenceChunks) && chatMessageService != null && chatMessageId != null) {
                chatMessageService.updateRagReferences(chatMessageId, ragReferenceChunks);
            }

            // 过滤掉chunkId为空的引用，一般是非知识库检索得到的结果
            ragReferencesDocs = ragReferencesDocs.stream().filter(reference -> reference.getChunkId() != null).collect(Collectors.toList());

            if (progressCallback != null && !CollectionUtils.isEmpty(ragReferencesDocs)) {
                progressCallback.accept("[REFERENCE]:" + JSON.toJSONString(ragReferencesDocs));
                log.info("[REFERENCE]:" + JSON.toJSONString(ragReferencesDocs));
            }
        } catch (Exception e) {
            log.warn("RAG引用信息回写失败: assistantMsgId={}", chatMessageId, e);
        }


        // 发送进度：聚合完成，即将进入LLM生成
        if (progressCallback != null) {
            progressCallback.accept("[PROGRESS]:正在生成回答...");
            log.info("[PROGRESS]:正在生成回答...");
        }
        return results;
    }
}
