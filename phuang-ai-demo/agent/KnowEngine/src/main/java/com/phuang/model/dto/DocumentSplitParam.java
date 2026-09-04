package com.phuang.model.dto;

public record DocumentSplitParam(Long documentId,
                                 String splitType,
                                 Integer chunkSize,
                                 Integer overlap,
                                 Integer titleLevel,
                                 String separator,
                                 String regex) {
}
