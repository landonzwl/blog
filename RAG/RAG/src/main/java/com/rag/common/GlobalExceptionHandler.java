package com.rag.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApi(ApiException e) {
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }

    /** 路径参数类型错误（如 /sessions/null/send）返回 400 而非 500 */
    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(Exception e) {
        return ResponseEntity.badRequest().body(Map.of("message", "请求参数格式错误，请刷新页面后重试"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleOther(Exception e) {
        log.error("未处理异常", e);
        return ResponseEntity.internalServerError().body(Map.of("message", "服务内部错误：" + e.getMessage()));
    }
}
