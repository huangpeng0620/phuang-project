package com.drstrong.health.arthas.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ArthasAgentGroup {

    /**
     * 服务名称
     */
    private String service;

    /**
     * 服务集群arthas节点信息
     */
    private List<ArthasAgent> agents;
}
