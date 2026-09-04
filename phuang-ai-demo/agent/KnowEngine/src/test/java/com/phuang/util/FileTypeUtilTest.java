package com.phuang.util;

import com.phuang.model.enums.FileType;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.Test;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;

public class FileTypeUtilTest {

    @Test
    public void detectsWordAndPowerPointContent() throws Exception {
        assertEquals(FileType.DOC, FileTypeUtil.getFileType(
                "sample.docx", new InMemoryMultipartFile("sample.docx", createWordDocument())));
        assertEquals(FileType.PPT, FileTypeUtil.getFileType(
                "sample.pptx", new InMemoryMultipartFile("sample.pptx", createPowerPointDocument())));
    }

    @Test
    public void detectsHtmlContent() {
        byte[] html = "<html><body>Tika HTML test</body></html>".getBytes(StandardCharsets.UTF_8);

        assertEquals(FileType.HTML, FileTypeUtil.getFileType(
                "sample.html", new InMemoryMultipartFile("sample.html", html)));
    }

    @Test
    public void keepsPlainTextFormatsSeparatedByExtension() {
        byte[] text = "plain text".getBytes(StandardCharsets.UTF_8);

        assertEquals(FileType.TXT, FileTypeUtil.getFileType(
                "sample.txt", new InMemoryMultipartFile("sample.txt", text)));
        assertEquals(FileType.MARKDOWN, FileTypeUtil.getFileType(
                "sample.md", new InMemoryMultipartFile("sample.md", text)));
        assertEquals(FileType.CSV, FileTypeUtil.getFileType(
                "sample.csv", new InMemoryMultipartFile("sample.csv", text)));
    }

    @Test
    public void recognizesAdditionalTikaExtensions() {
        assertEquals(FileType.RTF, FileTypeUtil.getFileType("sample.rtf"));
        assertEquals(FileType.ODT, FileTypeUtil.getFileType("sample.odt"));
        assertEquals(FileType.EPUB, FileTypeUtil.getFileType("sample.epub"));
        assertEquals(FileType.HTML, FileTypeUtil.getFileType("sample.xhtml"));
    }

    private byte[] createWordDocument() throws IOException {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("Word detection test");
            document.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    private byte[] createPowerPointDocument() throws IOException {
        try (XMLSlideShow presentation = new XMLSlideShow();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            presentation.createSlide().createTextBox().setText("PowerPoint detection test");
            presentation.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    private static class InMemoryMultipartFile implements MultipartFile {
        private final String originalFilename;
        private final byte[] content;

        private InMemoryMultipartFile(String originalFilename, byte[] content) {
            this.originalFilename = originalFilename;
            this.content = content;
        }

        @Override
        public String getName() {
            return "file";
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }

        @Override
        public String getContentType() {
            return null;
        }

        @Override
        public boolean isEmpty() {
            return content.length == 0;
        }

        @Override
        public long getSize() {
            return content.length;
        }

        @Override
        public byte[] getBytes() {
            return content.clone();
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(content);
        }

        @Override
        public void transferTo(File destination) throws IOException {
            Files.write(destination.toPath(), content);
        }
    }
}
