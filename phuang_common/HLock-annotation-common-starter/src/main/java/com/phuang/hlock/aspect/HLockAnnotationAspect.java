package com.phuang.hlock.aspect;

import com.phuang.hlock.annotation.HLock;
import com.phuang.hlock.handler.LockInfoHandler;
import com.phuang.hlock.model.BusinessException;
import com.phuang.hlock.model.LockFactory;
import com.phuang.hlock.model.LockInfo;
import com.phuang.hlock.model.enums.BusinessErrorEnum;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.springframework.core.annotation.Order;

import java.lang.reflect.Method;
import java.util.Objects;

@Aspect
@Slf4j
@Order(0)//确保比事务注解先执行，分布式锁在事务外
public class HLockAnnotationAspect {

    private final LockFactory lockFactory;

    private final LockInfoHandler lockInfoHandler;

    public HLockAnnotationAspect(LockFactory lockFactory, LockInfoHandler lockInfoHandler) {
        this.lockFactory = lockFactory;
        this.lockInfoHandler = lockInfoHandler;
    }

    /**
     * Rlock注解的环绕通知切面
     *
     * @param joinPoint
     * @return
     * @throws Throwable
     */
    @Around("@annotation(com.phuang.hlock.annotation.HLock)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        //获取方法对象的HLock分布锁注解
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        HLock hlock = method.getAnnotation(HLock.class);
        if (Objects.isNull(hlock)) {
            return joinPoint.proceed();
        }
        LockInfo lockInfo = lockInfoHandler.getLockInfo(joinPoint, hlock);
        /**
         * 设置全局超时时间配置
         */
        RLock lock = lockFactory.getLock(hlock, lockInfo.getLockName());
        log.debug("开始获取分布式锁, method={}, lockName={}, lockType={}, waitTime={}, leaseTime={}, unit={}",
                method.toGenericString(), lockInfo.getLockName(), hlock.lockType(),
                lockInfo.getWaitTime(), lockInfo.getLeaseTime(), hlock.unit());

        boolean tryLock;
        try {
            tryLock = lock.tryLock(lockInfo.getWaitTime(), lockInfo.getLeaseTime(), hlock.unit());
        } catch (InterruptedException interruptedException) {
            // tryLock 被中断后恢复线程中断标记，便于上层感知并终止后续任务。
            Thread.currentThread().interrupt();
            log.warn("获取分布式锁时线程被中断, method={}, lockName={}",
                    method.toGenericString(), lockInfo.getLockName());
            throw interruptedException;
        }
        if (!tryLock) {
            log.warn("获取分布式锁失败, method={}, lockName={}, lockType={}",
                    method.toGenericString(), lockInfo.getLockName(), hlock.lockType());
            // 直接传入错误枚举，确保异常同时保留 1001 错误码和对应提示信息。
            throw new BusinessException(BusinessErrorEnum.REPEATSUBMIT_ERROR);
        }
        log.debug("获取分布式锁成功, method={}, lockName={}",
                method.toGenericString(), lockInfo.getLockName());
        try {
            //加锁成功-->放行
            return joinPoint.proceed();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                //判断是否是当前线程持有锁 -> 是则释放锁
                try {
                    lock.unlock();
                    log.debug("释放分布式锁成功, method={}, lockName={}",
                            method.toGenericString(), lockInfo.getLockName());
                } catch (Exception unlockException) {
                    log.error("释放分布式锁失败, method={}, lockName={}",
                            method.toGenericString(), lockInfo.getLockName(), unlockException);
                }
            } else {
                log.warn("当前线程未持有分布式锁，跳过释放, method={}, lockName={}",
                        method.toGenericString(), lockInfo.getLockName());
            }
        }
    }

}
