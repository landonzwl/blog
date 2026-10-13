package com.np.schedule.common;

public enum ResultCode {

    SUCCESS(200, "success"),
    FAIL(500, "服务异常"),
    PARAM_INVALID(400, "参数校验失败"),
    FORBIDDEN(403, "无权访问");

    private final int code;
    private final String msg;

    ResultCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public int getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }
}
