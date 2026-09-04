package com.drstrong.health.arthas.model;

import com.alibaba.arthas.tunnel.server.AgentInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ArthasAgent {

    private String id;

    private AgentInfo info;

    private String envInfo;
}
