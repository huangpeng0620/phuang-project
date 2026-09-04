package com.phuang.autoconfigure.aspect;

import com.phuang.autoconfigure.annotation.NoRepeatSubmit;
import com.phuang.autoconfigure.model.BusinessErrorEnum;
import com.phuang.autoconfigure.model.BusinessException;
import com.phuang.autoconfigure.model.NoRepeatSubmitProperties;
import com.phuang.autoconfigure.model.RepeatType;
import com.phuang.autoconfigure.utils.AssertUtils;
import com.phuang.autoconfigure.utils.SpElUtils;
import jodd.util.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.Objects;

/**
 * @author huangpeng
 * @description RepeatSubmitAnnotationAspect
 * @since 2023/8/17
 */
@Aspect
@Component
@Slf4j
@Order(1)
public class NoRepeatSubmitAnnotationAspect {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private NoRepeatSubmitProperties noRepeatSubmitProperties;

    private static final String REPEAT_UUID = "REPEAT_UUID";

    private static final String SPLIT = "_";

    /**
     * RepeatSubmit注解的环绕通知切面
     *
     * @param joinPoint
     * @return
     * @throws Throwable
     */
    @Around("@annotation(com.phuang.autoconfigure.annotation.NoRepeatSubmit)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        //获取注解方法对象
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        NoRepeatSubmit noRepeatSubmit = method.getAnnotation(NoRepeatSubmit.class);
        if (Objects.isNull(noRepeatSubmit)) {
            joinPoint.proceed();
        }
        RepeatType type = noRepeatSubmit.type();
        String key = null;

        if (Objects.equals(type, RepeatType.Header)) {
            /**
             * 获取请求头唯一UUID
             */
            key = getHeader();
            AssertUtils.isNull(key, BusinessErrorEnum.ILLEGALITY_REQUEST_ERROR);
        } else if (Objects.equals(type, RepeatType.Parm)) {
            /**
             * 获取key的前缀,为空则使用默认格式：类名#方法名
             */
            String prefix = StringUtil.isBlank(noRepeatSubmit.prefixKey()) ? SpElUtils.getMethodKey(method) : noRepeatSubmit.prefixKey();
            key = SpElUtils.parseSpEl(method, joinPoint.getArgs(), noRepeatSubmit.key());
            key = String.join(SPLIT, prefix, key);
        } else {
            log.error("暂不支持该防重注解方式！");
            throw new BusinessException(BusinessErrorEnum.SYSTEM_ERROR);
        }
        /**
         * 获取全局有效时间
         */
        long expireTime = Objects.nonNull(noRepeatSubmitProperties.getExpireTime()) ? noRepeatSubmitProperties.getExpireTime() : noRepeatSubmit.expireTime();
        Boolean absent = stringRedisTemplate.opsForValue().setIfAbsent(key, "", expireTime, noRepeatSubmit.unit());
        AssertUtils.isTrue(absent, BusinessErrorEnum.REPEATSUBMIT_ERROR);
        return joinPoint.proceed();
    }

    /**
     * 获取请求头唯一的幂等请求头REPEAT_UUID
     *
     * @return
     */
    private String getHeader() {
        ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = requestAttributes.getRequest();
        return request.getHeader(REPEAT_UUID);
    }
}
