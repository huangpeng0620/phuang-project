package com.phuang.autoconfigure.annotation;

import com.phuang.autoconfigure.model.RepeatType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * @author huangpeng
 * @description 防重注解
 * @since 2023/8/17
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface NoRepeatSubmit {

    /**
     * 限制时常
     */
    long expireTime() default 5L;

    /**
     * 防重方式：默认为请求头
     */
    RepeatType type() default RepeatType.Header;

    /**
     * 业务前缀
     */
    String prefixKey() default "";

    /**
     * 参数部分
     */
    String key() default "";

    /**
     * 时间单位，默认秒
     */
    TimeUnit unit() default TimeUnit.SECONDS;
}
