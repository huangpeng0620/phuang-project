package com.phuang.service.document;

import org.apache.tika.Tika;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

/**
 * 使用 Apache Tika 从通用文档中提取纯文本
 */
@Component
public class TikaDocumentParser {

    private final Tika tika = new Tika();
    private final int maxTextLength;

    public TikaDocumentParser(@Value("${tika.max-text-length:5000000}") int maxTextLength) {
        if (maxTextLength <= 0) {
            throw new IllegalArgumentException("tika.max-text-length 必须大于 0");
        }
        this.maxTextLength = maxTextLength;
    }

    public ParsedDocument parse(InputStream inputStream, String resourceName) throws Exception {
        if (inputStream == null) {
            throw new IOException("待解析的文件流不能为空");
        }

        Metadata metadata = new Metadata();
        if (resourceName != null && !resourceName.isBlank()) {
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, resourceName);
        }

        String text = tika.parseToString(inputStream, metadata, maxTextLength);
        return new ParsedDocument(normalizeText(text), metadata.get(HttpHeaders.CONTENT_TYPE));
    }

    private String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace("\u0000", "")
                .strip();
    }

    public record ParsedDocument(String text, String mediaType) {
    }
}
