package com.phuang.autoconfigure.annotation;

import com.phuang.autoconfigure.model.LockType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * huangpeng
 * description：分布式锁注解(运行时生效、作用在方法上)
 * 2023/8/14 23:40
 *
 * @author phuang
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Rlock {

    /**
     * key的前缀,默认取方法全限定名,除非需要在不同方法上对同一个资源做分布式锁,就自己指定
     */
    String prefixKey() default "";

    /**
     * springEl 表达式
     */
    String key();

    /**
     * 等待锁时间:默认-1(不等待直接失败,redisson默认也是-1)
     */
    int waitTime() default -1;

    /**
     * 持有锁时间：默认-1(表示开启Redisson的看门狗机制,值不为-1表示关闭)
     */
    int leaseTime() default -1;

    /**
     * 时间单位,默认秒
     */
    TimeUnit unit() default TimeUnit.SECONDS;

    /**
     * 锁类型,默认可重入锁
     */
    LockType lockType() default LockType.Reentrant;


}
