package com.workorder.back.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 * Controller 抛出的异常在此统一转成 Result，前端拿到结构化错误信息
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务异常：把异常消息原样回传 */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /** 参数校验失败：@Valid 触发，提取第一个字段错误 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValid(MethodArgumentNotValidException e) {
        FieldError field = e.getBindingResult().getFieldError();
        String msg = field != null ? field.getDefaultMessage() : ResultCode.PARAM_INVALID.getMsg();
        log.warn("参数校验失败: {}", msg);
        return Result.fail(ResultCode.PARAM_INVALID.getCode(), msg);
    }

    /** 其他绑定异常（表单非 @Valid） */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBind(BindException e) {
        FieldError field = e.getBindingResult().getFieldError();
        String msg = field != null ? field.getDefaultMessage() : ResultCode.PARAM_INVALID.getMsg();
        log.warn("参数绑定失败: {}", msg);
        return Result.fail(ResultCode.PARAM_INVALID.getCode(), msg);
    }

    /** 兜底：未预期的异常 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleOther(Exception e) {
        log.error("未预期异常", e);
        return Result.fail(ResultCode.FAIL.getCode(), ResultCode.FAIL.getMsg());
    }
}
