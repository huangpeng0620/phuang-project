package com.phuang.hlock.handler;

import com.phuang.hlock.annotation.HLock;
import com.phuang.hlock.config.HLockConfigProperties;
import com.phuang.hlock.model.LockInfo;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import static org.assertj.core.api.Assertions.assertThat;

class LockInfoHandlerTest {

    @Test
    void resolvesImplementationMethodBehindAnInterfaceProxy() throws Exception {
        Method interfaceMethod = DocumentService.class.getMethod("split", DocumentSplitParam.class);
        Method implementationMethod = DocumentServiceImpl.class.getMethod("split", DocumentSplitParam.class);
        HLock hLock = implementationMethod.getAnnotation(HLock.class);

        MethodSignature signature = proxy(MethodSignature.class, (proxy, method, args) -> {
            if ("getMethod".equals(method.getName())) {
                return interfaceMethod;
            }
            throw new UnsupportedOperationException(method.getName());
        });
        DocumentServiceImpl target = new DocumentServiceImpl();
        JoinPoint joinPoint = proxy(JoinPoint.class, (proxy, method, args) -> switch (method.getName()) {
            case "getSignature" -> signature;
            case "getTarget" -> target;
            case "getArgs" -> new Object[]{new DocumentSplitParam(42L)};
            default -> throw new UnsupportedOperationException(method.getName());
        });

        LockInfo lockInfo = new LockInfoHandler(new HLockConfigProperties()).getLockInfo(joinPoint, hLock);

        assertThat(lockInfo.getLockName()).isEqualTo("HLOCK_document-split_42");
        assertThat(lockInfo.getWaitTime()).isZero();
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> interfaceType, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(interfaceType.getClassLoader(), new Class<?>[]{interfaceType}, handler);
    }

    private interface DocumentService {

        Integer split(DocumentSplitParam documentSplitParam);
    }

    private static final class DocumentServiceImpl implements DocumentService {

        @Override
        @HLock(prefixKey = "document-split", key = "#p0.documentId", waitTime = 0)
        public Integer split(DocumentSplitParam documentSplitParam) {
            return 1;
        }
    }

    private record DocumentSplitParam(Long documentId) {
    }
}
