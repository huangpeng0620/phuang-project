package com.phuang.hlock.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum BusinessErrorEnum implements ErrorEnum {

    SYSTEM_ERROR("-1", "系统错误"),

    REPEATSUBMIT_ERROR("1001", "请稍后重试"),

    ILLEGALITY_REQUEST_ERROR("1002", "非法请求");

    private final String code;

    private final String msg;

    /**
     * 适配统一异常枚举接口，避免业务异常构造时丢失实际错误码。
     */
    @Override
    public String getErrorCode() {
        return code;
    }

    /**
     * 适配统一异常枚举接口，返回枚举定义的业务错误信息。
     */
    @Override
    public String getErrorMsg() {
        return msg;
    }
}
