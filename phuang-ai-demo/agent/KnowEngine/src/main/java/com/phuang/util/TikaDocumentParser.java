package com.phuang.util;

import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.WriteLimitReachedException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.WriteOutContentHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;

/**
 * 使用 Apache Tika 从通用文档中提取纯文本
 */
@Component
public class TikaDocumentParser {

    private final Parser parser = new AutoDetectParser();

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

        WriteOutContentHandler textHandler = new WriteOutContentHandler(maxTextLength);
        ParseContext parseContext = new ParseContext();
        parseContext.set(Parser.class, parser);

        try (TikaInputStream tikaInputStream = TikaInputStream.get(inputStream)) {
            parser.parse(tikaInputStream, new BodyContentHandler(textHandler), metadata, parseContext);
        } catch (SAXException e) {
            if (WriteLimitReachedException.isWriteLimitReached(e)) {
                throw new TikaException("Tika 提取文本超过上限: " + maxTextLength, e);
            }
            throw new TikaException("Tika 解析文本失败", e);
        }
        return new ParsedDocument(normalizeText(textHandler.toString()), metadata.get(HttpHeaders.CONTENT_TYPE));
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
