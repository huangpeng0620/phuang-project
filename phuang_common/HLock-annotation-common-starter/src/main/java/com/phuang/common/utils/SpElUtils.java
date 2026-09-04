package com.phuang.common.utils;

import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * spring el表达式解析
 */
public class SpElUtils {

    /**
     * 解析表达式
     */
    private static final ExpressionParser parser = new SpelExpressionParser();

    /**
     * 获取方法参数名称
     */
    private static final DefaultParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    /**
     * 解析PEL表达式
     *
     * @param method 方法对象
     * @param args   方法传入的用户参数
     * @param spEl   spel表达式
     * @return
     */
    public static String parseSpEl(Method method, Object[] args, String spEl) {
        if (!StringUtils.hasText(spEl)) {
            throw new IllegalArgumentException("HLock key expression must not be blank");
        }
        //获取方法参数数组
        String[] params = Optional.ofNullable(parameterNameDiscoverer.getParameterNames(method)).orElse(new String[]{});
        //解析和计算SpEL表达式的上下文对象
        EvaluationContext context = new StandardEvaluationContext();
        for (int i = 0; i < params.length; i++) {
            context.setVariable(params[i], args[i]);
        }
        Expression expression = parser.parseExpression(spEl);
        return expression.getValue(context, String.class);
    }

    /**
     * 获取分布式锁默认的方法名(类名+方法名)
     *
     * @param method
     * @return
     */
    public static String getMethodKey(Method method) {
        return String.join("#", method.getDeclaringClass().toString(), method.getName());
    }
}
