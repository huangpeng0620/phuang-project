package com.phuang.common.utils;

import com.phuang.hlock.model.HlockException;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionException;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * spring el表达式解析
 */
public class SpElUtils {

    /**
     * 解析表达式
     */
    private static final ExpressionParser PARSER = new SpelExpressionParser();

    /**
     * 注解中的表达式数量有限，缓存解析结果以避免每次加锁时重复解析。
     */
    private static final Map<String, Expression> EXPRESSION_CACHE = new ConcurrentHashMap<>();

    /**
     * 获取方法参数名称
     */
    private static final ParameterNameDiscoverer PARAMETER_NAME_DISCOVERER = createParameterNameDiscoverer();

    /**
     * 解析PEL表达式
     *
     * @param method 方法对象
     * @param args   方法传入的用户参数
     * @param spEl   spel表达式
     * @return
     */
    public static String parseSpEl(Method method, Object[] args, String spEl) {
        if (method == null) {
            throw new HlockException("HLock method must not be null");
        }
        if (!StringUtils.hasText(spEl)) {
            throw new HlockException("HLock key expression must not be blank");
        }
        Object[] arguments = args == null ? new Object[0] : args;
        try {
            EvaluationContext context = new MethodBasedEvaluationContext(
                    null, method, arguments, PARAMETER_NAME_DISCOVERER);
            Expression expression = EXPRESSION_CACHE.computeIfAbsent(spEl, PARSER::parseExpression);
            String value = expression.getValue(context, String.class);
            if (value == null) {
                throw new HlockException("HLock key expression evaluated to null: " + spEl
                        + ", method: " + method.toGenericString());
            }
            return value;
        } catch (HlockException ex) {
            throw ex;
        } catch (ExpressionException ex) {
            throw new HlockException("Failed to evaluate HLock key expression '" + spEl
                    + "' for method " + method.toGenericString()
                    + ". Prefer #p0/#a0 aliases when parameter names are unavailable.", ex);
        } catch (RuntimeException ex) {
            throw new HlockException("Failed to evaluate HLock key expression '" + spEl
                    + "' for method " + method.toGenericString(), ex);
        }
    }

    private static ParameterNameDiscoverer createParameterNameDiscoverer() {
        DefaultParameterNameDiscoverer discoverer = new DefaultParameterNameDiscoverer();
        discoverer.addDiscoverer(new LocalVariableTableParameterNameDiscoverer());
        return discoverer;
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
