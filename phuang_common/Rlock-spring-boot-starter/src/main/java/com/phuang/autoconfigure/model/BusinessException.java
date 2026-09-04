package com.phuang.autoconfigure.model;

import java.util.Objects;

/**
 * huangpeng
 * 2023/8/15 11:00
 */
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    protected String errorCode;

    public BusinessException() {
        super(BusinessErrorEnum.SYSTEM_ERROR.getErrorMsg());
    }

    public BusinessException(String errorMsg) {
        super(errorMsg);
        this.errorCode = BusinessErrorEnum.SYSTEM_ERROR.getCode();
    }

    public BusinessException(ErrorEnum errorEnum) {
        super(errorEnum.getErrorMsg());
        this.errorCode = errorEnum.getErrorCode();
    }

    public BusinessException(String errorCode, String errorMsg) {
        super(errorMsg);
        if (Objects.isNull(errorCode)) {
            errorCode = BusinessErrorEnum.SYSTEM_ERROR.getCode();
        }
        this.errorCode = errorCode;
    }
}
