package com.phuang.autoconfigure.handler;

import com.phuang.autoconfigure.annotation.Rlock;
import com.phuang.autoconfigure.config.RlockConfigProperties;
import com.phuang.autoconfigure.model.LockInfo;
import com.phuang.autoconfigure.utils.SpElUtils;
import jodd.util.StringUtil;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.lang.reflect.Method;

/**
 * @author huangpeng
 * @description LockInfoHandler
 * @since 2024/6/11
 */
@Component
public class LockInfoHandler {

    @Resource
    private RlockConfigProperties rlockConfigProperties;

    private static final String LOCK_NAME_PREFIX = "rLock";

    private static final String LOCK_NAME_SEPARATOR = "_";

    public LockInfo getLockInfo(JoinPoint joinPoint, Rlock rlock) {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        String businessPrefix = StringUtil.isBlank(rlock.prefixKey()) ? SpElUtils.getMethodKey(method) : rlock.prefixKey();
        String businessKey = SpElUtils.parseSpEl(method, joinPoint.getArgs(), rlock.key());
        /**
         * 获取key的前缀,为空则使用默认格式:类名_方法名
         */
        String lockName = String.join(LOCK_NAME_SEPARATOR, LOCK_NAME_PREFIX, businessPrefix, businessKey);
        Long waitTime = rlock.waitTime() == -1 ? rlockConfigProperties.getWaitTime() : rlock.waitTime();
        Long leaseTime = rlock.leaseTime() == -1 ? rlockConfigProperties.getLeaseTime() : rlock.leaseTime();
        return LockInfo.builder()
                .lockType(rlock.lockType())
                .lockName(lockName)
                .waitTime(waitTime)
                .leaseTime(leaseTime)
                .build();
    }

}
