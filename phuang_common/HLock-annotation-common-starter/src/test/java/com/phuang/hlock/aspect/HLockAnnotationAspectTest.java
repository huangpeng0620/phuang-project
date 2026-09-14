package com.phuang.hlock.aspect;

import com.phuang.hlock.annotation.HLock;
import com.phuang.hlock.config.HLockConfigProperties;
import com.phuang.hlock.handler.LockInfoHandler;
import com.phuang.hlock.model.HlockException;
import com.phuang.hlock.model.LockFactory;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

class HLockAnnotationAspectTest {

    @Test
    void convertsInterruptedLockAttemptAndRestoresInterruptFlag() throws Exception {
        Method method = TestService.class.getMethod("split", DocumentSplitParam.class);
        RLock lock = proxy(RLock.class, (proxy, invokedMethod, args) -> {
            if ("tryLock".equals(invokedMethod.getName())) {
                throw new InterruptedException("interrupted");
            }
            throw new UnsupportedOperationException(invokedMethod.getName());
        });

        try {
            assertThatThrownBy(() -> aspect(lock).around(joinPoint(method, () -> 1)))
                    .isInstanceOf(HlockException.class)
                    .hasMessageContaining("Interrupted while acquiring distributed lock")
                    .hasCauseInstanceOf(InterruptedException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void throwsHlockExceptionWhenUnlockFailsAfterSuccessfulBusinessCall() throws Exception {
        Method method = TestService.class.getMethod("split", DocumentSplitParam.class);
        IllegalStateException unlockFailure = new IllegalStateException("unlock failed");
        RLock lock = lockThatFailsOnUnlock(unlockFailure);

        assertThatThrownBy(() -> aspect(lock).around(joinPoint(method, () -> 1)))
                .isInstanceOf(HlockException.class)
                .hasMessageContaining("Failed to release distributed lock")
                .hasCause(unlockFailure);
    }

    @Test
    void preservesBusinessFailureAndSuppressesUnlockFailure() throws Exception {
        Method method = TestService.class.getMethod("split", DocumentSplitParam.class);
        IllegalArgumentException businessFailure = new IllegalArgumentException("business failed");
        IllegalStateException unlockFailure = new IllegalStateException("unlock failed");

        Throwable actual = catchThrowable(() -> aspect(lockThatFailsOnUnlock(unlockFailure))
                .around(joinPoint(method, () -> {
                    throw businessFailure;
                })));

        assertThat(actual).isSameAs(businessFailure);
        assertThat(actual.getSuppressed()).hasSize(1);
        assertThat(actual.getSuppressed()[0])
                .isInstanceOf(HlockException.class)
                .hasCause(unlockFailure);
    }

    private static HLockAnnotationAspect aspect(RLock lock) {
        RedissonClient redissonClient = proxy(RedissonClient.class, (proxy, method, args) -> {
            if ("getLock".equals(method.getName())) {
                return lock;
            }
            throw new UnsupportedOperationException(method.getName());
        });
        HLockConfigProperties properties = new HLockConfigProperties();
        return new HLockAnnotationAspect(
                new LockFactory(redissonClient), new LockInfoHandler(properties));
    }

    private static RLock lockThatFailsOnUnlock(RuntimeException unlockFailure) {
        return proxy(RLock.class, (proxy, method, args) -> switch (method.getName()) {
            case "tryLock", "isHeldByCurrentThread" -> true;
            case "unlock" -> throw unlockFailure;
            default -> throw new UnsupportedOperationException(method.getName());
        });
    }

    private static ProceedingJoinPoint joinPoint(Method targetMethod, ProceedAction proceedAction) {
        MethodSignature signature = proxy(MethodSignature.class, (proxy, method, args) -> {
            if ("getMethod".equals(method.getName())) {
                return targetMethod;
            }
            throw new UnsupportedOperationException(method.getName());
        });
        TestService target = new TestService();
        return proxy(ProceedingJoinPoint.class, (proxy, method, args) -> switch (method.getName()) {
            case "getSignature" -> signature;
            case "getTarget" -> target;
            case "getArgs" -> new Object[]{new DocumentSplitParam(42L)};
            case "proceed" -> proceedAction.proceed();
            default -> throw new UnsupportedOperationException(method.getName());
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> interfaceType, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(interfaceType.getClassLoader(), new Class<?>[]{interfaceType}, handler);
    }

    @FunctionalInterface
    private interface ProceedAction {

        Object proceed() throws Throwable;
    }

    private static final class TestService {

        @HLock(prefixKey = "document-split", key = "#p0.documentId", waitTime = 0)
        public Integer split(DocumentSplitParam documentSplitParam) {
            return 1;
        }
    }

    private record DocumentSplitParam(Long documentId) {
    }
}
