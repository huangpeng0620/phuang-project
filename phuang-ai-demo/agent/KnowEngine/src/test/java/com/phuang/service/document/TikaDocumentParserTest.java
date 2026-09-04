package com.phuang.service.document;

import com.phuang.model.enums.FileType;
import com.phuang.model.enums.KnowledgeBaseType;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TikaDocumentParserTest {

    private final TikaDocumentParser parser = new TikaDocumentParser(1_000_000);

    @Test
    public void parsesWordDocument() throws Exception {
        byte[] content;
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("Word Tika integration test");
            document.write(outputStream);
            content = outputStream.toByteArray();
        }

        TikaDocumentParser.ParsedDocument result =
                parser.parse(new ByteArrayInputStream(content), "sample.docx");

        assertTrue(result.text().contains("Word Tika integration test"));
    }

    @Test
    public void parsesPowerPointDocument() throws Exception {
        byte[] content;
        try (XMLSlideShow presentation = new XMLSlideShow();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            XSLFSlide slide = presentation.createSlide();
            XSLFTextBox textBox = slide.createTextBox();
            textBox.setText("PowerPoint Tika integration test");
            presentation.write(outputStream);
            content = outputStream.toByteArray();
        }

        TikaDocumentParser.ParsedDocument result =
                parser.parse(new ByteArrayInputStream(content), "sample.pptx");

        assertTrue(result.text().contains("PowerPoint Tika integration test"));
    }

    @Test
    public void parsesHtmlAndIgnoresMarkup() throws Exception {
        String html = "<html><head><title>Example</title></head>"
                + "<body><h1>Tika HTML test</h1><script>ignored()</script></body></html>";

        TikaDocumentParser.ParsedDocument result = parser.parse(
                new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)), "sample.html");

        assertTrue(result.text().contains("Tika HTML test"));
        assertFalse(result.text().contains("<h1>"));
    }

    @Test
    public void processorSupportsOnlyGenericSearchDocuments() {
        TikaProcessServiceImpl service = new TikaProcessServiceImpl();

        assertTrue(service.supports(FileType.DOC, KnowledgeBaseType.DOCUMENT_SEARCH));
        assertTrue(service.supports(FileType.PPT, KnowledgeBaseType.DOCUMENT_SEARCH));
        assertTrue(service.supports(FileType.HTML, KnowledgeBaseType.DOCUMENT_SEARCH));
        assertTrue(service.supports(FileType.RTF, KnowledgeBaseType.DOCUMENT_SEARCH));
        assertTrue(service.supports(FileType.ODT, KnowledgeBaseType.DOCUMENT_SEARCH));
        assertTrue(service.supports(FileType.EPUB, KnowledgeBaseType.DOCUMENT_SEARCH));
        assertFalse(service.supports(FileType.PDF, KnowledgeBaseType.DOCUMENT_SEARCH));
        assertFalse(service.supports(FileType.DOC, KnowledgeBaseType.DATA_QUERY));
    }
}
