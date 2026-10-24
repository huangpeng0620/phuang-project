package com.phuang.config;

import io.agentscope.core.session.mysql.MysqlSession;
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
     * session 持久化
     * @param dataSource mysql数据源
     * @return
     */
    @Bean
    public MysqlSession mysqlSession(DataSource dataSource) {
        return new MysqlSession(dataSource, true);
    }
}
