package com.phuang.autoconfigure.model;

import com.phuang.autoconfigure.annotation.Rlock;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * @author huangpeng
 * @description LockFactory
 * @since 2023/8/15
 */
@Component
public class LockFactory {

    @Resource
    private RedissonClient redissonClient;

    public RLock getLock(Rlock lock, String key) {
        switch (lock.lockType()) {
            case Reentrant:
                return redissonClient.getLock(key);
            case Fair:
                return redissonClient.getFairLock(key);
            case Read:
                return redissonClient.getReadWriteLock(key).readLock();
            case Write:
                return redissonClient.getReadWriteLock(key).writeLock();
            default:
                throw new BusinessException("do not support lock type");
        }
    }
}
