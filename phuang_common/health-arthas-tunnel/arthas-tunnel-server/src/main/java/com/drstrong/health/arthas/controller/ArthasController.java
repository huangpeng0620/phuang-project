package com.drstrong.health.arthas.controller;

import com.alibaba.arthas.tunnel.server.AgentInfo;
import com.alibaba.arthas.tunnel.server.TunnelServer;
import com.alibaba.arthas.tunnel.server.app.configuration.ArthasProperties;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.drstrong.health.arthas.config.AuthExtProperties;
import com.drstrong.health.arthas.model.ArthasAgent;
import com.drstrong.health.arthas.model.ArthasAgentGroup;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;


@RequestMapping("/arthas")
@RestController
public class ArthasController {

    @Value("${arthas.agent.split:@}")
    private String arthasAgentSplit;

    @Autowired
    private TunnelServer tunnelServer;

    @Autowired
    private ArthasProperties arthasProperties;

    @Autowired
    private AuthExtProperties authExtProperties;

    @Resource
    private ReactiveUserDetailsService userDetailsService;

    @Autowired
    private DiscoveryClient discoveryClient;

    /**
     * 获取tunnel server服务信息
     *
     * @return
     */
    @GetMapping("/server")
    public ArthasProperties.Server getTunnelServerInfo() {
        return arthasProperties.getServer();
    }

    /**
     * 获取arthas agentId中应用名和随机值的分隔符
     *
     * @return
     */
    @GetMapping("/agent/split")
    public String getArthasAgentSplit() {
        return arthasAgentSplit;
    }

    /**
     * 根据权限获取访问的arthas agents列表
     *
     * @param principal
     * @return
     */
    @GetMapping("/access/agents")
    public List<ArthasAgentGroup> getAgents(Principal principal) {
        Set<String> roles = getCurrentUserRole(principal.getName());
        if (CollectionUtils.isEmpty(roles)) {
            return Lists.newArrayList();
        }
        boolean isSuperUser = isSuperAdmin(roles);
        Map<String, AgentInfo> agentInfoMap = tunnelServer.getAgentInfoMap();
        Map<String, List<ArthasAgent>> map = Maps.newHashMap();
        agentInfoMap.forEach((agentId, info) -> {
            String[] split = agentId.split(arthasAgentSplit, 2);
            String serviceName = split[0];
            String envInfo = getData(serviceName, info.getHost());
            if (isSuperUser || accessApp(roles, serviceName)) {
                List<ArthasAgent> agents = map.computeIfAbsent(serviceName, k1 -> new ArrayList<>());
                ArthasAgent arthasAgent = ArthasAgent.builder()
                        .id(split[1])
                        .info(info)
                        .envInfo(envInfo)
                        .build();
                agents.add(arthasAgent);
            }
        });
        List<ArthasAgentGroup> serviceNameAndArthasAgentInfoMap = new ArrayList<>();
        map.forEach((service, agents) -> {
            ArthasAgentGroup group = ArthasAgentGroup.builder()
                    .service(service)
                    .agents(agents)
                    .build();
            serviceNameAndArthasAgentInfoMap.add(group);
        });
        return serviceNameAndArthasAgentInfoMap;
    }

    /**
     * 获取服务节点元数据信息
     *
     * @param serviceName 服务名称
     * @param host        服务集群某个节点IP
     * @return
     */
    private String getData(String serviceName, String host) {
        List<ServiceInstance> instances = discoveryClient.getInstances(serviceName);
        if (CollectionUtils.isEmpty(instances)) {
            return null;
        }
        Map<String, List<ServiceInstance>> hostAndServiceInstanceMap = instances.stream().collect(Collectors.groupingBy(ServiceInstance::getHost));
        List<ServiceInstance> serviceInstance = hostAndServiceInstanceMap.get(host);
        if (CollectionUtils.isEmpty(serviceInstance)) {
            return null;
        }
        Map<String, String> metadata = serviceInstance.get(0).getMetadata();
        if (Objects.isNull(metadata) || metadata.size() == 0) {
            return null;
        }
        return Objects.nonNull(metadata.get("env")) ? "env_" + metadata.get("env") : null;
    }

    private Set<String> getCurrentUserRole(String userName) {
        if (Strings.isNullOrEmpty(userName)) {
            return Sets.newHashSet();
        }
        UserDetails userDetails = userDetailsService.findByUsername(userName).block();
        if (Objects.isNull(userDetails)) {
            return Collections.emptySet();
        }
        return userDetails.getAuthorities().stream().filter(g -> g.getAuthority() != null)
                .map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
    }

    private boolean isSuperAdmin(Set<String> roles) {
        return accessApp(roles, authExtProperties.getSuperAdminRoleSign());
    }

    private boolean accessApp(Set<String> roles, String appName) {
        for (String role : roles) {
            if (role.endsWith(appName)) {
                return Boolean.TRUE;
            }
        }
        return Boolean.FALSE;
    }
}
