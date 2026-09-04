package com.phuang.handler.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 文档上传完成事件
 *
 * <p>该事件只携带持久化后的主键，监听器在上传事务提交后重新查询文档和版本，
 * 避免跨线程传递 {@code MultipartFile} 或使用事务中的临时实体。</p>
 */
@Getter
public class DocumentUploadedEvent extends ApplicationEvent {

    private final Long documentId;

    private final Long documentVersionId;

    public DocumentUploadedEvent(Object source, Long documentId, Long documentVersionId) {
        super(source);
        this.documentId = documentId;
        this.documentVersionId = documentVersionId;
    }
}
