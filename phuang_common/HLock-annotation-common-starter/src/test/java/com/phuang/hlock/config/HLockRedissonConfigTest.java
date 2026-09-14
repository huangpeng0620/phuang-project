package com.phuang.hlock.config;

import com.phuang.hlock.model.HlockException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HLockRedissonConfigTest {

    @Test
    void reportsInvalidConfigurationWithHlockException() {
        HLockConfigProperties properties = new HLockConfigProperties();
        properties.setWaitTime(-2);

        assertThatThrownBy(() -> new HLockRedissonConfig(properties).redissonClient())
                .isInstanceOf(HlockException.class)
                .hasMessage("redis.hlock.wait-time must be -1 or >= 0")
                .hasFieldOrPropertyWithValue("errorCode", "-1");
    }
}
