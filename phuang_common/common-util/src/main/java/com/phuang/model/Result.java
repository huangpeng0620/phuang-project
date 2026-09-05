package com.phuang.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.Date;

/**
 *
 * @description Result
 * @author huangpeng
 * @since 2026/9/5
 */
@ApiModel(description = "统一响应实体")
@JsonIgnoreProperties(ignoreUnknown = true)
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "结果是否成功")
    private boolean success;

    @ApiModelProperty(value = "操作成功或失败后的提示信息")
    private String msg;

    @ApiModelProperty(value = "数据内容")
    private T data;

    @ApiModelProperty(value = "响应时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date timestamp;

    /**
     * 供 Jackson 等序列化框架使用。
     */
    public Result() {
        this.timestamp = new Date();
    }

    /**
     * 使用当前时间创建响应结果。
     */
    public Result(boolean success, String msg, T data) {
        this(success, msg, data, new Date());
    }

    /**
     * 创建指定响应时间的结果。
     */
    public Result(boolean success, String msg, T data, Date timestamp) {
        this.success = success;
        this.msg = msg;
        this.data = data;
        setTimestamp(timestamp);
    }

    public static <T> Result<T> success() {
        return new Result<>(true, null, null);
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(true, null, data);
    }

    public static <T> Result<T> success(String msg, T data) {
        return new Result<>(true, msg, data);
    }

    public static <T> Result<T> fail(String msg) {
        return new Result<>(false, msg, null);
    }

    public static <T> Result<T> fail(String msg, T data) {
        return new Result<>(false, msg, data);
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public Date getTimestamp() {
        return new Date(timestamp.getTime());
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp == null ? new Date() : new Date(timestamp.getTime());
    }

}
