package com.phuang.autoconfigure.aspect;

import com.phuang.autoconfigure.annotation.Rlock;
import com.phuang.autoconfigure.handler.LockInfoHandler;
import com.phuang.autoconfigure.model.BusinessErrorEnum;
import com.phuang.autoconfigure.model.BusinessException;
import com.phuang.autoconfigure.model.LockFactory;
import com.phuang.autoconfigure.model.LockInfo;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.lang.reflect.Method;
import java.util.Objects;

/**
 * phuang
 * 2023/8/14 23:44
 */
@Aspect
@Component
@Slf4j
@Order(0)//确保比事务注解先执行，分布式锁在事务外
public class RLockAnnotationAspect {
    //custom-annotation-common-start

    @Resource
    private LockFactory lockFactory;

    @Resource
    private LockInfoHandler lockInfoHandler;

    /**
     * Rlock注解的环绕通知切面
     *
     * @param joinPoint
     * @return
     * @throws Throwable
     */
    @Around("@annotation(com.phuang.autoconfigure.annotation.Rlock)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        //获取方法对象的Rlock分布锁注解
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Rlock rlock = method.getAnnotation(Rlock.class);
        if (Objects.isNull(rlock)) {
            joinPoint.proceed();
        }
        LockInfo lockInfo = lockInfoHandler.getLockInfo(joinPoint, rlock);
        /**
         * 设置全局超时时间配置 TODO
         */
        RLock lock = lockFactory.getLock(rlock, lockInfo.getLockName());
        //boolean tryLock = lock.tryLock(rlock.waitTime(), rlock.leaseTime(), rlock.unit());
        boolean tryLock = lock.tryLock(lockInfo.getWaitTime(), lockInfo.getLeaseTime(), rlock.unit());
        if (!tryLock) {
            throw new BusinessException(BusinessErrorEnum.REPEATSUBMIT_ERROR);
        }
        try {
            //加锁成功-->放行
            return joinPoint.proceed();
        } catch (Exception e) {
            throw new BusinessException(BusinessErrorEnum.SYSTEM_ERROR);
        } finally {
            //释放锁
            lock.unlock();
        }
    }

}
