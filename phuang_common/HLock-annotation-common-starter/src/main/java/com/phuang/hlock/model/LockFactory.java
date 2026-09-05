package com.phuang.hlock.model;

import com.phuang.hlock.annotation.HLock;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

public class LockFactory {

    private final RedissonClient redissonClient;

    public LockFactory(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    public RLock getLock(HLock lock, String key) {
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
                throw new HlockException("do not support lock type");
        }
    }
}
