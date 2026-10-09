package com.workorder.back.common;

/**
 * 业务状态码枚举
 * 200 成功；4xx 客户端错误；5xx 服务端错误
 */
public enum ResultCode {

    SUCCESS(200, "success"),
    FAIL(500, "服务异常"),
    PARAM_INVALID(400, "参数校验失败"),
    UNAUTHORIZED(401, "未登录或 token 已过期"),
    FORBIDDEN(403, "无权访问"),
    NOT_FOUND(404, "资源不存在");

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
