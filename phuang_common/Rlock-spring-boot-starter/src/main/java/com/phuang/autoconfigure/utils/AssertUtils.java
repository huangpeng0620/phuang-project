package com.phuang.autoconfigure.utils;

import com.phuang.autoconfigure.model.BusinessErrorEnum;
import com.phuang.autoconfigure.model.BusinessException;

/**
 * huangpeng
 * 2023/8/15 11:09
 */
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
            throw new BusinessException(errorEnum.getErrorMsg());
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
            throw new BusinessException(errorEnum.getErrorMsg());
        }
    }

    public static void isNull(Object object, String code, String message) {
        if (object != null) {
            throw new BusinessException(code, message);
        }
    }
}
