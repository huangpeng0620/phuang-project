package com.phuang.hlock.handler;

import com.phuang.common.utils.SpElUtils;
import com.phuang.hlock.annotation.HLock;
import com.phuang.hlock.config.HLockConfigProperties;
import com.phuang.hlock.model.HlockException;
import com.phuang.hlock.model.LockInfo;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;

public class LockInfoHandler {

    private final HLockConfigProperties hLockConfigProperties;

    public LockInfoHandler(HLockConfigProperties hLockConfigProperties) {
        this.hLockConfigProperties = hLockConfigProperties;
    }

    private static final String LOCK_NAME_PREFIX = "HLOCK";

    private static final String LOCK_NAME_SEPARATOR = "_";

    public LockInfo getLockInfo(JoinPoint joinPoint, HLock hlock) {
        try {
            Method signatureMethod = ((MethodSignature) joinPoint.getSignature()).getMethod();
            Object target = joinPoint.getTarget();
            Method method = target == null
                    ? signatureMethod
                    : AopUtils.getMostSpecificMethod(signatureMethod, target.getClass());
            String businessPrefix = StringUtils.hasText(hlock.prefixKey())
                    ? hlock.prefixKey()
                    : SpElUtils.getMethodKey(method);
            String businessKey = SpElUtils.parseSpEl(method, joinPoint.getArgs(), hlock.key());
            // 获取 key 的前缀，为空则使用默认格式：类名_方法名。
            String lockName = String.join(LOCK_NAME_SEPARATOR, LOCK_NAME_PREFIX, businessPrefix, businessKey);
            Long waitTime = hlock.waitTime() == -1 ? hLockConfigProperties.getWaitTime() : hlock.waitTime();
            Long leaseTime = hlock.leaseTime() == -1 ? hLockConfigProperties.getLeaseTime() : hlock.leaseTime();
            return LockInfo.builder()
                    .lockType(hlock.lockType())
                    .lockName(lockName)
                    .waitTime(waitTime)
                    .leaseTime(leaseTime)
                    .build();
        } catch (HlockException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new HlockException("Failed to create HLock lock information", ex);
        }
    }

}
