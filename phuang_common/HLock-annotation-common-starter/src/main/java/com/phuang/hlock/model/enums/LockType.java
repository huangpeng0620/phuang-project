package com.phuang.hlock.model.enums;

import lombok.NoArgsConstructor;

/**
 * @description 锁类型
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
