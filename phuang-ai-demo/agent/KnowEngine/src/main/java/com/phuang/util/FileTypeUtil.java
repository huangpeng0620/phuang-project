package com.phuang.util;

import com.phuang.model.enums.FileType;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.io.TikaInputStream;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;

@Slf4j
public class FileTypeUtil {

    private static final Tika TIKA = new Tika();

    public static FileType getFileType(String fileName, MultipartFile file) {
        if (file == null) {
            return null;
        }

        FileType extensionType = getFileType(fileName);
        try (TikaInputStream inputStream = TikaInputStream.get(file.getInputStream())) {
            String mimeType = TIKA.detect(inputStream, fileName);
            FileType detectedType = fromMimeType(mimeType);

            // Tika 无法仅靠内容可靠地区分普通文本、Markdown 和 CSV，此时以后缀为准。
            if (detectedType == FileType.TXT && isPlainTextFamily(extensionType)) {
                return extensionType;
            }
            if (detectedType != null) {
                if (extensionType != null && extensionType != detectedType) {
                    log.warn("文件扩展名与内容类型不一致, fileName={}, extensionType={}, mimeType={}",
                            fileName, extensionType, mimeType);
                }
                return detectedType;
            }
        } catch (IOException e) {
            log.error("文件类型检测失败, fileName={}: {}", fileName, e.getMessage());
        }
        return extensionType;
    }

    public static FileType getFileType(String fileName) {
        if (fileName == null) {
            return null;
        }
        String normalizedFileName = fileName.toLowerCase(Locale.ROOT);

        if (hasExtension(normalizedFileName, ".pdf")) {
            return FileType.PDF;
        }
        if (hasExtension(normalizedFileName, ".csv", ".tsv")) {
            return FileType.CSV;
        }
        if (hasExtension(normalizedFileName, ".xlsx", ".xls", ".xlsm", ".xlsb")) {
            return FileType.EXCEL;
        }
        if (hasExtension(normalizedFileName, ".doc", ".docx", ".docm", ".dot", ".dotx", ".dotm")) {
            return FileType.DOC;
        }
        if (hasExtension(normalizedFileName, ".ppt", ".pptx", ".pptm", ".pps", ".ppsx")) {
            return FileType.PPT;
        }
        if (hasExtension(normalizedFileName, ".html", ".htm", ".xhtml")) {
            return FileType.HTML;
        }
        if (hasExtension(normalizedFileName, ".rtf")) {
            return FileType.RTF;
        }
        if (hasExtension(normalizedFileName, ".odt")) {
            return FileType.ODT;
        }
        if (hasExtension(normalizedFileName, ".epub")) {
            return FileType.EPUB;
        }
        if (hasExtension(normalizedFileName, ".txt")) {
            return FileType.TXT;
        }
        if (hasExtension(normalizedFileName, ".md", ".markdown")) {
            return FileType.MARKDOWN;
        }
        return null;
    }

    private static FileType fromMimeType(String mimeType) {
        if (mimeType == null) {
            return null;
        }
        String normalizedMimeType = mimeType.toLowerCase(Locale.ROOT).split(";", 2)[0].trim();
        return switch (normalizedMimeType) {
            case "application/pdf" -> FileType.PDF;
            case "text/csv", "text/tab-separated-values" -> FileType.CSV;
            case "application/vnd.ms-excel",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "application/vnd.ms-excel.sheet.macroenabled.12",
                    "application/vnd.ms-excel.sheet.binary.macroenabled.12" -> FileType.EXCEL;
            case "application/msword",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.template",
                    "application/vnd.ms-word.document.macroenabled.12",
                    "application/vnd.ms-word.template.macroenabled.12" -> FileType.DOC;
            case "application/vnd.ms-powerpoint",
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                    "application/vnd.openxmlformats-officedocument.presentationml.slideshow",
                    "application/vnd.openxmlformats-officedocument.presentationml.template",
                    "application/vnd.ms-powerpoint.presentation.macroenabled.12",
                    "application/vnd.ms-powerpoint.slideshow.macroenabled.12" -> FileType.PPT;
            case "text/html", "application/xhtml+xml" -> FileType.HTML;
            case "application/rtf", "text/rtf" -> FileType.RTF;
            case "application/vnd.oasis.opendocument.text",
                    "application/x-vnd.oasis.opendocument.text" -> FileType.ODT;
            case "application/epub+zip" -> FileType.EPUB;
            case "text/markdown", "text/x-markdown", "application/markdown" -> FileType.MARKDOWN;
            case "text/plain", "application/txt" -> FileType.TXT;
            default -> null;
        };
    }

    private static boolean isPlainTextFamily(FileType fileType) {
        return fileType == FileType.TXT || fileType == FileType.MARKDOWN || fileType == FileType.CSV;
    }

    private static boolean hasExtension(String fileName, String... extensions) {
        for (String extension : extensions) {
            if (fileName.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }

}
