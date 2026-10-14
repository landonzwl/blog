package com.rag.common;

/** 业务异常，统一返回 400 与 message */
public class ApiException extends RuntimeException {

    public ApiException(String message) {
        super(message);
    }
}
