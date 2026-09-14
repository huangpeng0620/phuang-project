package com.phuang.common.utils;

import com.phuang.hlock.model.HlockException;
import org.junit.jupiter.api.Test;
import org.springframework.asm.ClassWriter;
import org.springframework.asm.Label;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.Opcodes;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpElUtilsTest {

    @Test
    void evaluatesPositionAliasesWithoutDependingOnParameterNames() throws Exception {
        Method method = SampleService.class.getDeclaredMethod(
                "split", DocumentSplitParam.class, String.class);
        Object[] arguments = {new DocumentSplitParam(42L), "tenant-a"};

        assertThat(SpElUtils.parseSpEl(method, arguments, "#p0.documentId")).isEqualTo("42");
        assertThat(SpElUtils.parseSpEl(method, arguments, "#a0.documentId")).isEqualTo("42");
        assertThat(SpElUtils.parseSpEl(method, arguments, "#p1")).isEqualTo("tenant-a");
    }

    @Test
    void evaluatesLegacyNamedParameterFromLocalVariableTable() throws Exception {
        byte[] classBytes = generateServiceWithoutMethodParameters();
        Class<?> serviceClass = new ByteArrayClassLoader(classBytes).loadGeneratedClass();
        Method method = serviceClass.getDeclaredMethod("split", Object.class);

        assertThat(method.getParameters()[0].isNamePresent()).isFalse();
        assertThat(SpElUtils.parseSpEl(method,
                new Object[]{Map.of("documentId", 42L)},
                "#documentSplitParam['documentId']")).isEqualTo("42");
    }

    @Test
    void reportsHowToHandleAnUnavailableNamedParameter() throws Exception {
        Method method = SampleService.class.getDeclaredMethod(
                "split", DocumentSplitParam.class, String.class);

        assertThatThrownBy(() -> SpElUtils.parseSpEl(
                method, new Object[]{new DocumentSplitParam(42L), "tenant-a"}, "#missing.documentId"))
                .isInstanceOf(HlockException.class)
                .hasMessageContaining("Prefer #p0/#a0 aliases")
                .hasFieldOrPropertyWithValue("errorCode", "-1");
    }

    @Test
    void rejectsBlankExpressionWithHlockException() throws Exception {
        Method method = SampleService.class.getDeclaredMethod(
                "split", DocumentSplitParam.class, String.class);

        assertThatThrownBy(() -> SpElUtils.parseSpEl(method, new Object[0], " "))
                .isInstanceOf(HlockException.class)
                .hasMessage("HLock key expression must not be blank");
    }

    private static byte[] generateServiceWithoutMethodParameters() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, ByteArrayClassLoader.INTERNAL_CLASS_NAME,
                null, "java/lang/Object", null);

        MethodVisitor constructor = writer.visitMethod(
                Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(1, 1);
        constructor.visitEnd();

        MethodVisitor method = writer.visitMethod(
                Opcodes.ACC_PUBLIC, "split", "(Ljava/lang/Object;)V", null, null);
        method.visitCode();
        Label start = new Label();
        Label end = new Label();
        method.visitLabel(start);
        method.visitInsn(Opcodes.RETURN);
        method.visitLabel(end);
        method.visitLocalVariable("this", "L" + ByteArrayClassLoader.INTERNAL_CLASS_NAME + ";",
                null, start, end, 0);
        method.visitLocalVariable("documentSplitParam", "Ljava/lang/Object;", null, start, end, 1);
        method.visitMaxs(0, 2);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static final class ByteArrayClassLoader extends ClassLoader {

        private static final String INTERNAL_CLASS_NAME = "com/phuang/common/utils/GeneratedService";
        private static final String CLASS_NAME = INTERNAL_CLASS_NAME.replace('/', '.');
        private static final String RESOURCE_NAME = INTERNAL_CLASS_NAME + ".class";

        private final byte[] classBytes;

        private ByteArrayClassLoader(byte[] classBytes) {
            super(SpElUtilsTest.class.getClassLoader());
            this.classBytes = classBytes;
        }

        private Class<?> loadGeneratedClass() {
            return defineClass(CLASS_NAME, classBytes, 0, classBytes.length);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            if (RESOURCE_NAME.equals(name)) {
                return new ByteArrayInputStream(classBytes);
            }
            return super.getResourceAsStream(name);
        }
    }

    private record DocumentSplitParam(Long documentId) {
    }

    @SuppressWarnings("unused")
    private static final class SampleService {

        private Integer split(DocumentSplitParam documentSplitParam, String tenantId) {
            return documentSplitParam.documentId().intValue();
        }
    }
}
