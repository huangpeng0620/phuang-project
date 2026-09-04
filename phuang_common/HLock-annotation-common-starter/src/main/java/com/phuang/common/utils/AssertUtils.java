package com.phuang.common.utils;

import com.phuang.hlock.model.BusinessException;
import com.phuang.hlock.model.enums.BusinessErrorEnum;

public class AssertUtils {

    public AssertUtils() {
    }

    public static void isTrue(boolean expression, String message) {
        if (!expression) {
            throw new BusinessException(message);
        }
    }

    public static void isTrue(boolean expression, BusinessErrorEnum errorEnum) {
        if (!expression) {
            throw new BusinessException(errorEnum);
        }
    }

    public static void isTrue(boolean expression, String code, String message) {
        if (!expression) {
            throw new BusinessException(code, message);
        }
    }

    public static void notTrue(boolean expression, String message) {
        if (expression) {
            throw new BusinessException(message);
        }
    }

    public static void notTrue(boolean expression, String code, String message) {
        if (expression) {
            throw new BusinessException(code, message);
        }
    }

    public static void notNull(Object object, String message) {
        if (object == null) {
            throw new BusinessException(message);
        }
    }

    public static void notNull(Object object, String code, String message) {
        if (object == null) {
            throw new BusinessException(code, message);
        }
    }

    public static void isNull(Object object, String message) {
        if (object != null) {
            throw new BusinessException(message);
        }
    }

    public static void isNull(Object object, BusinessErrorEnum errorEnum) {
        if (object != null) {
            throw new BusinessException(errorEnum);
        }
    }

    public static void isNull(Object object, String code, String message) {
        if (object != null) {
            throw new BusinessException(code, message);
        }
    }
}
