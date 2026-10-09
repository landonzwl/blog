package com.workorder.back.common;

/**
 * 业务异常
 * Service 层抛出后由 GlobalExceptionHandler 统一捕获，避免 try/catch 满天飞
 */
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(String msg) {
        super(msg);
        this.code = ResultCode.FAIL.getCode();
    }

    public BusinessException(int code, String msg) {
        super(msg);
        this.code = code;
    }

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMsg());
        this.code = resultCode.getCode();
    }

    public int getCode() {
        return code;
    }
}
