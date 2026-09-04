package com.phuang.autoconfigure.model;

import lombok.NoArgsConstructor;

/**
 * @author huangpeng
 * @description 锁类型
 * @since 2023/8/15
 */
@NoArgsConstructor
public enum LockType {
    /**
     * 可重入锁
     */
    Reentrant,
    /**
     * 公平锁
     */
    Fair,
    /**
     * 读锁
     */
    Read,
    /**
     * 写锁
     */
    Write;
}
