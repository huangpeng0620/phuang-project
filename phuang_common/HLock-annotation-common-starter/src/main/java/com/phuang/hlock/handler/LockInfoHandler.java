package com.phuang.hlock.handler;

import com.phuang.common.utils.SpElUtils;
import com.phuang.hlock.annotation.HLock;
import com.phuang.hlock.config.HLockConfigProperties;
import com.phuang.hlock.model.LockInfo;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.lang.reflect.Method;

@Component
public class LockInfoHandler {

    @Resource
    private HLockConfigProperties hLockConfigProperties;

    private static final String LOCK_NAME_PREFIX = "HLOCK";

    private static final String LOCK_NAME_SEPARATOR = "_";

    public LockInfo getLockInfo(JoinPoint joinPoint, HLock hlock) {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        String businessPrefix = StringUtils.isEmpty(hlock.prefixKey()) ? SpElUtils.getMethodKey(method) : hlock.prefixKey();
        String businessKey = SpElUtils.parseSpEl(method, joinPoint.getArgs(), hlock.key());
        /**
         * 获取key的前缀,为空则使用默认格式:类名_方法名
         */
        String lockName = String.join(LOCK_NAME_SEPARATOR, LOCK_NAME_PREFIX, businessPrefix, businessKey);
        Long waitTime = hlock.waitTime() == -1 ? hLockConfigProperties.getWaitTime() : hlock.waitTime();
        Long leaseTime = hlock.leaseTime() == -1 ? hLockConfigProperties.getLeaseTime() : hlock.leaseTime();
        return LockInfo.builder()
                .lockType(hlock.lockType())
                .lockName(lockName)
                .waitTime(waitTime)
                .leaseTime(leaseTime)
                .build();
    }

}
