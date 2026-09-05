package com.phuang.handler.exception;

import com.phuang.hlock.model.HlockException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 将业务异常转换为调用方可识别的 HTTP 响应
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 捕获 @rlock 分布式锁异常
     * @param exception
     * @return
     */
    @ExceptionHandler(HlockException.class)
    public ResponseEntity<Map<String, String>> handleBusinessException(HlockException exception) {
        Map<String, String> response = new LinkedHashMap<>();
        response.put("code", exception.getErrorCode());
        response.put("message", exception.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }
}
