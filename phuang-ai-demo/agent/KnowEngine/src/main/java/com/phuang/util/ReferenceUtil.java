package com.phuang.util;

import com.phuang.model.entity.ChatMessageEntity;
import com.phuang.model.enums.RetrievalSource;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;

import java.util.List;
import java.util.stream.Collectors;

import static com.phuang.model.constant.MetadataKeyConstant.*;

public class ReferenceUtil {

    public static List<ChatMessageEntity.RagReference> getRagReferences(List<Content> contents, RetrievalSource retrievalSource) {
        return contents.stream().map(content ->
                ChatMessageEntity.RagReference.builder()
                        .documentId(content.textSegment().metadata().getInteger(DOC_ID) + "")
                        .documentTitle(content.textSegment().metadata().getString(FILE_NAME))
                        .url(content.textSegment().metadata().getString(URL))
                        .chunkId(content.textSegment().metadata().getString(CHUNK_ID))
                        .chunkContent(content.textSegment().text())
                        .similarityScore((Double) content.metadata().get(ContentMetadata.SCORE))
                        .retrievalSource(retrievalSource)
                        .rerankScore((Double) content.metadata().get(ContentMetadata.RERANKED_SCORE))
                        .build()).collect(Collectors.toList());
    }

    public static ChatMessageEntity.RagReference getRagReference(Content content, RetrievalSource retrievalSource) {
        return ChatMessageEntity.RagReference.builder()
                .documentId(content.textSegment().metadata().getInteger(DOC_ID) + "")
                .documentTitle(content.textSegment().metadata().getString(FILE_NAME))
                .url(content.textSegment().metadata().getString(URL))
                .chunkId(content.textSegment().metadata().getString(CHUNK_ID))
                .chunkContent(content.textSegment().text())
                .similarityScore((Double) content.metadata().get(ContentMetadata.SCORE))
                .retrievalSource(retrievalSource)
                .rerankScore((Double) content.metadata().get(ContentMetadata.RERANKED_SCORE))
                .build();
    }
}
