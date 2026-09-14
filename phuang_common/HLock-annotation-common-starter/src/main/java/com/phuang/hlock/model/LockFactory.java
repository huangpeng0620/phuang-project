package com.phuang.hlock.model;

import com.phuang.hlock.annotation.HLock;
import com.phuang.hlock.model.enums.LockType;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

public class LockFactory {

    private final RedissonClient redissonClient;

    public LockFactory(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    public RLock getLock(HLock lock, String key) {
        LockType lockType = null;
        try {
            if (lock == null) {
                throw new HlockException("HLock annotation must not be null");
            }
            lockType = lock.lockType();
            if (lockType == null) {
                throw new HlockException("HLock lock type must not be null");
            }
            switch (lockType) {
                case Reentrant:
                    return redissonClient.getLock(key);
                case Fair:
                    return redissonClient.getFairLock(key);
                case Read:
                    return redissonClient.getReadWriteLock(key).readLock();
                case Write:
                    return redissonClient.getReadWriteLock(key).writeLock();
                default:
                    throw new HlockException("do not support lock type: " + lockType);
            }
        } catch (HlockException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new HlockException("Failed to obtain Redisson lock, lockType=" + lockType
                    + ", lockName=" + key, ex);
        }
    }
}
