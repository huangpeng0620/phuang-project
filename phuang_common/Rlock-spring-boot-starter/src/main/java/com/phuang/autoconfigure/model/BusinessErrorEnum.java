package com.phuang.autoconfigure.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;

@AllArgsConstructor
public enum BusinessErrorEnum implements ErrorEnum {

    SYSTEM_ERROR("-1", "系统错误"),
    REPEATSUBMIT_ERROR("1001", "请稍后重试"),
    ILLEGALITY_REQUEST_ERROR("1002", "非法请求");

    private String code;

    private String msg;

    @Override
    public String getErrorCode() {
        return "";
    }

    @Override
    public String getErrorMsg() {
        return "";
    }
}
