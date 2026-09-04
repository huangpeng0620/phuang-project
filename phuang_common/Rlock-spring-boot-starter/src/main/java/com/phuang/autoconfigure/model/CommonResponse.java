package com.phuang.autoconfigure.model;

import lombok.Data;

import java.io.Serializable;

/**
 * huangpeng
 * 2023/8/15 11:17
 */
@Data
public class CommonResponse<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 成功标识
     */
    private Boolean success;
    /**
     * 错误码
     */
    private String errCode;
    /**
     * 错误消息
     */
    private String errMsg;
    /**
     * 返回对象
     */
    private T data;

    public static <T> CommonResponse<T> success() {
        CommonResponse<T> result = new CommonResponse<T>();
        result.setData(null);
        result.setSuccess(Boolean.TRUE);
        return result;
    }

    public static <T> CommonResponse<T> success(T data) {
        CommonResponse<T> result = new CommonResponse<T>();
        result.setData(data);
        result.setSuccess(Boolean.TRUE);
        return result;
    }

    public static <T> CommonResponse<T> fail(String code, String msg) {
        CommonResponse<T> result = new CommonResponse<T>();
        result.setSuccess(Boolean.FALSE);
        result.setErrCode(code);
        result.setErrMsg(msg);
        return result;
    }

    public static <T> CommonResponse<T> fail(ErrorEnum errorEnum) {
        CommonResponse<T> result = new CommonResponse<T>();
        result.setSuccess(Boolean.FALSE);
        result.setErrCode(errorEnum.getErrorCode());
        result.setErrMsg(errorEnum.getErrorMsg());
        return result;
    }

    public boolean isSuccess() {
        return this.success;
    }
}
