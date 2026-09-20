package com.phuang.handler.job;

import com.phuang.service.graph.GraphBuildService;
import com.xxl.job.core.handler.annotation.XxlJob;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * 待处理图谱任务补偿和历史资料回填任务
 * 在 XXL-Job Admin 中按需配置两个 handler；任务每次只取有限数量，避免长时间占用执行器。
 */
@Component
public class GraphBuildJob {

    /** 构图任务登记、领取和构建服务。 */
    @Resource
    private GraphBuildService graphBuildService;

    /**
     * 处理切分事件未执行时留下的 PENDING 任务。
     * build 本身会原子领取，故与正常事件并发运行也不会重复构建。
     */
    @XxlJob("documentGraphBuild")
    public void processPendingTasks() {
        for (Long versionId : graphBuildService.pendingVersionIds(20)) {
            graphBuildService.build(versionId);
        }
    }

    /**
     * 一次性或分批回填已手动切分的历史当前版本，只补登记构图任务。
     * 尚未切分的文档仍等待用户调用切分接口；重复运行不会清空 Neo4j。
     */
    @XxlJob("documentGraphBackfill")
    public void backfillCurrentDocuments() {
        graphBuildService.enqueueMissingCurrentVersions(100);
        processPendingTasks();
    }
}
