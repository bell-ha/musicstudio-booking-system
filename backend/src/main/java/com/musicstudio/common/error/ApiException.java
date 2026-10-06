package com.musicstudio.common.error;

import org.springframework.http.HttpStatus;

/**
 * 업무 규칙 위반을 Problem Details 응답으로 바꾸기 위한 예외.
 * type은 docs/architecture/02-API.md 1절의 오류 종류, code는 세부 원인이다.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String type;
    private final String code;

    public ApiException(HttpStatus status, String type, String code, String title) {
        super(title);
        this.status = status;
        this.type = type;
        this.code = code;
    }

    public static ApiException conflict(String code, String title) {
        return new ApiException(HttpStatus.CONFLICT, "conflict", code, title);
    }

    public static ApiException invalid(String code, String title) {
        return new ApiException(HttpStatus.BAD_REQUEST, "validation-failed", code, title);
    }

    public HttpStatus status() {
        return status;
    }

    public String type() {
        return type;
    }

    public String code() {
        return code;
    }
}
