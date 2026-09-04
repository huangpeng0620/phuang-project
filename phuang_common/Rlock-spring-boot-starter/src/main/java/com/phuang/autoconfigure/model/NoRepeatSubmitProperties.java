package com.phuang.autoconfigure.model;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @author huangpeng
 * @description NoRepeatSubmitProperties
 * @since 2023/8/22
 */
@ConfigurationProperties(prefix = NoRepeatSubmitProperties.NOREPEATSUBMIT_PREFIX)
@Data
@Component
public class NoRepeatSubmitProperties {

    public static final String NOREPEATSUBMIT_PREFIX = "phuang";

    private long expireTime;

}
