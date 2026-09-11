package com.phuang.model.dto;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.rag.content.DefaultContent;
import lombok.EqualsAndHashCode;

import java.util.Map;

@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class KnowEngineDefaultContent extends DefaultContent {

    public KnowEngineDefaultContent(TextSegment textSegment, Map<ContentMetadata, Object> metadata) {
        super(textSegment, metadata);
    }

    public KnowEngineDefaultContent(DefaultContent defaultContent) {
        super(defaultContent.textSegment(), defaultContent.metadata());
    }

    public KnowEngineDefaultContent(String text) {
        super(text);
    }

    public KnowEngineDefaultContent(TextSegment textSegment) {
        super(textSegment);
    }

    @EqualsAndHashCode.Include
    private Object embeddingId() {
        return metadata().get(ContentMetadata.EMBEDDING_ID);
    }

}
