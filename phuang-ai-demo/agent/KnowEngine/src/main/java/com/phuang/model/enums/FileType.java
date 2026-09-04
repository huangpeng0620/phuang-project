package com.phuang.model.enums;

/**
 * 文件类型
 */
public enum FileType {
    PDF("pdf"),
    DOC("doc"),
    TXT("txt"),
    HTML("html"),
    MARKDOWN("markdown"),
    CSV("csv"),
    EXCEL("excel"),
    PPT("ppt"),
    RTF("rtf"),
    ODT("odt"),
    EPUB("epub");

    private final String type;

    FileType(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }
}
