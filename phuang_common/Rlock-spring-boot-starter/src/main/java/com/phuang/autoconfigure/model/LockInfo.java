package com.phuang.autoconfigure.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author huangpeng
 * @description 锁的基本信息
 * @since 2024/6/11
 */
@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class LockInfo {

    private LockType lockType;

    private String lockName;

    private Long waitTime;

    private Long leaseTime;
}
