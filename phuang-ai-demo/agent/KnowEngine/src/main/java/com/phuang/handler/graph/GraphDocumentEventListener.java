package com.phuang.handler.graph;

import com.phuang.handler.event.DocumentChunkedEvent;
import com.phuang.service.graph.GraphBuildService;
import jakarta.annotation.Resource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 分段事务提交后异步构图，与原有向量化监听器并行工作。
 * 构图异常由任务表记录为 FAILED，不影响上传或向量化流程。
 */
@Component
public class GraphDocumentEventListener {

    /** 图谱构建的统一幂等入口。 */
    @Resource
    private GraphBuildService graphBuildService;

    /** 分段及构图任务都提交成功后，立即尝试处理该版本。 */
    @Async("eventListenerExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDocumentChunked(DocumentChunkedEvent event) {
        graphBuildService.build(event.getDocumentVersionId());
    }
}
