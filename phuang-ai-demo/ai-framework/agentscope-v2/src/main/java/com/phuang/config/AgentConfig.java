package com.phuang.config;

import io.agentscope.extensions.jdbc.JdbcDistributedStore;
import io.agentscope.extensions.mysql.state.MysqlAgentStateStore;
import io.agentscope.harness.agent.DistributedStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 *
 * @description AgentConfig
 * @author huangpeng
 * @since 2026/10/24
 */
@Configuration
public class AgentConfig {

    /**
     * agent mysql 持久化
     * @param dataSource mysql数据源
     * @return
     */
    @Bean
    public MysqlAgentStateStore mysqlAgentStateStore(DataSource dataSource) {
        return new MysqlAgentStateStore(dataSource, true);
    }

    /**
     * jdbcDistributedStore
     * @param dataSource
     * @return
     */
    @Bean
    public DistributedStore jdbcDistributedStore(DataSource dataSource) {
        return JdbcDistributedStore.create(dataSource);
    }
}
