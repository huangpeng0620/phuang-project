package com.phuang.hlock.model;

import com.phuang.hlock.model.enums.LockType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @description 锁的基本信息
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
