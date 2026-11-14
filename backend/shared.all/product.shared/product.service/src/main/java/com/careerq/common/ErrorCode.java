package com.careerq.common;

import org.springframework.http.HttpStatus;

/**
 * 业务错误码。与接口契约中的错误 code 保持一致。
 */
public enum ErrorCode {

    INVALID_PARAM(40001, HttpStatus.BAD_REQUEST, "参数不合法"),
    UNAUTHORIZED(40100, HttpStatus.UNAUTHORIZED, "未登录"),
    FORBIDDEN(40300, HttpStatus.FORBIDDEN, "无权限"),
    NOT_FOUND(40400, HttpStatus.NOT_FOUND, "资源不存在"),
    CONFLICT(40900, HttpStatus.CONFLICT, "状态冲突"),
    RATE_LIMITED(42900, HttpStatus.TOO_MANY_REQUESTS, "请求过于频繁"),
    INTERNAL_ERROR(50000, HttpStatus.INTERNAL_SERVER_ERROR, "服务异常");

    private final int code;
    private final HttpStatus status;
    private final String message;

    ErrorCode(int code, HttpStatus status, String message) {
        this.code = code;
        this.status = status;
        this.message = message;
    }

    public int code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }

    public String message() {
        return message;
    }
}
