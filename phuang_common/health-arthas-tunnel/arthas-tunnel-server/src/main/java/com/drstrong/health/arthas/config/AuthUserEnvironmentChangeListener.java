package com.drstrong.health.arthas.config;

import com.drstrong.health.arthas.service.PropertiesSourceMapReactiveUserDetailsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Resource;
import java.util.Set;
import java.util.concurrent.TimeUnit;


@Configuration
@Slf4j
public class AuthUserEnvironmentChangeListener implements ApplicationListener<EnvironmentChangeEvent> {

    @Resource
    private PropertiesSourceMapReactiveUserDetailsService propertiesSourceMapReactiveUserDetailsService;


    @Override
    public void onApplicationEvent(EnvironmentChangeEvent event) {
        if (!isUserChanged(event)) {
            return;
        }
        new Thread(() -> {
            try {
                TimeUnit.SECONDS.sleep(5);
            } catch (InterruptedException ignored) {
            }
            propertiesSourceMapReactiveUserDetailsService.updateUsers();
        }).start();
    }

    private boolean isUserChanged(EnvironmentChangeEvent event) {
        Set<String> keys = event.getKeys();
        for (String key : keys) {
            if (key.startsWith(AuthExtProperties.PREFIX)) {
                return true;
            }
        }
        return false;
    }
}
