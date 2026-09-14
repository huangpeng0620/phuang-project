package com.phuang.common.utils;

import org.springframework.asm.ClassReader;
import org.springframework.asm.ClassVisitor;
import org.springframework.asm.Label;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.Opcodes;
import org.springframework.asm.Type;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.util.ClassUtils;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 从 class 文件的本地变量表读取方法参数名，兼容未开启 {@code -parameters}
 * 但保留了调试信息的旧业务项目。
 *
 * <p>Spring 6 不再提供原来的本地变量表参数名发现器，因此 starter 在标准反射
 * 发现失败后使用这个轻量回退。编译时同时关闭参数名和调试信息时，应改用
 * {@code #p0}/{@code #a0} 位置参数。</p>
 */
final class LocalVariableTableParameterNameDiscoverer implements ParameterNameDiscoverer {

    private final ClassValue<Map<Method, String[]>> parameterNamesCache = new ClassValue<>() {
        @Override
        protected Map<Method, String[]> computeValue(Class<?> type) {
            return inspectClass(type);
        }
    };

    @Override
    public String[] getParameterNames(Method method) {
        Method bridgedMethod = BridgeMethodResolver.findBridgedMethod(method);
        Map<Method, String[]> parameterNames = parameterNamesCache.get(bridgedMethod.getDeclaringClass());
        String[] names = parameterNames.get(bridgedMethod);
        return names == null ? null : names.clone();
    }

    @Override
    public String[] getParameterNames(Constructor<?> constructor) {
        return null;
    }

    private Map<Method, String[]> inspectClass(Class<?> type) {
        String classFileName = ClassUtils.getClassFileName(type);
        try (InputStream inputStream = type.getResourceAsStream(classFileName)) {
            if (inputStream == null) {
                return Collections.emptyMap();
            }
            Map<Method, String[]> parameterNames = new HashMap<>();
            new ClassReader(inputStream).accept(
                    new ParameterNameClassVisitor(type, parameterNames), ClassReader.SKIP_FRAMES);
            return parameterNames;
        } catch (IOException | IllegalArgumentException | LinkageError ex) {
            return Collections.emptyMap();
        }
    }

    private static final class ParameterNameClassVisitor extends ClassVisitor {

        private final Class<?> type;
        private final Map<Method, String[]> parameterNames;

        private ParameterNameClassVisitor(Class<?> type, Map<Method, String[]> parameterNames) {
            super(Opcodes.ASM9);
            this.type = type;
            this.parameterNames = parameterNames;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            if (name.startsWith("<") || (access & (Opcodes.ACC_SYNTHETIC | Opcodes.ACC_BRIDGE)) != 0) {
                return null;
            }
            return new ParameterNameMethodVisitor(type, parameterNames, access, name, descriptor);
        }
    }

    private static final class ParameterNameMethodVisitor extends MethodVisitor {

        private final Class<?> type;
        private final Map<Method, String[]> parameterNames;
        private final String methodName;
        private final Type[] argumentTypes;
        private final String[] discoveredNames;
        private final int[] argumentSlots;

        private ParameterNameMethodVisitor(Class<?> type, Map<Method, String[]> parameterNames,
                                           int access, String methodName, String descriptor) {
            super(Opcodes.ASM9);
            this.type = type;
            this.parameterNames = parameterNames;
            this.methodName = methodName;
            this.argumentTypes = Type.getArgumentTypes(descriptor);
            this.discoveredNames = new String[argumentTypes.length];
            this.argumentSlots = computeArgumentSlots((access & Opcodes.ACC_STATIC) != 0, argumentTypes);
        }

        @Override
        public void visitLocalVariable(String name, String descriptor, String signature,
                                       Label start, Label end, int index) {
            for (int i = 0; i < argumentSlots.length; i++) {
                if (argumentSlots[i] == index && discoveredNames[i] == null) {
                    discoveredNames[i] = name;
                }
            }
        }

        @Override
        public void visitEnd() {
            if (!allParameterNamesDiscovered()) {
                return;
            }
            try {
                Class<?>[] parameterTypes = new Class<?>[argumentTypes.length];
                ClassLoader classLoader = type.getClassLoader();
                for (int i = 0; i < argumentTypes.length; i++) {
                    parameterTypes[i] = ClassUtils.resolveClassName(argumentTypes[i].getClassName(), classLoader);
                }
                parameterNames.put(type.getDeclaredMethod(methodName, parameterTypes), discoveredNames.clone());
            } catch (NoSuchMethodException | LinkageError ex) {
                // 无法解析的方法不参与参数名回退，位置参数仍然可用。
            }
        }

        private boolean allParameterNamesDiscovered() {
            for (String discoveredName : discoveredNames) {
                if (discoveredName == null) {
                    return false;
                }
            }
            return true;
        }

        private static int[] computeArgumentSlots(boolean staticMethod, Type[] argumentTypes) {
            int[] slots = new int[argumentTypes.length];
            int nextSlot = staticMethod ? 0 : 1;
            for (int i = 0; i < argumentTypes.length; i++) {
                slots[i] = nextSlot;
                nextSlot += argumentTypes[i].getSize();
            }
            return slots;
        }
    }
}
