package com.phuang.hlock.model;

import com.phuang.hlock.model.enums.BusinessErrorEnum;
import com.phuang.hlock.model.enums.ErrorEnum;

import java.util.Objects;

public class HlockException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    protected String errorCode;

    public HlockException() {
        super(BusinessErrorEnum.SYSTEM_ERROR.getMsg());
        this.errorCode = BusinessErrorEnum.SYSTEM_ERROR.getCode();
    }

    public HlockException(String errorMsg) {
        super(errorMsg);
        this.errorCode = BusinessErrorEnum.SYSTEM_ERROR.getCode();
    }

    public HlockException(ErrorEnum errorEnum) {
        super(errorEnum.getErrorMsg());
        this.errorCode = errorEnum.getErrorCode();
    }

    public HlockException(String errorCode, String errorMsg) {
        super(errorMsg);
        if (Objects.isNull(errorCode)) {
            errorCode = BusinessErrorEnum.SYSTEM_ERROR.getCode();
        }
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
