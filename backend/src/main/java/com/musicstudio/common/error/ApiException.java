package com.musicstudio.common.error;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;

/**
 * 업무 규칙 위반을 Problem Details 응답으로 바꾸기 위한 예외.
 * type은 docs/architecture/02-API.md 1절의 오류 종류, code는 세부 원인이다.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String type;
    private final String code;
    private final List<Map<String, String>> errors;

    public ApiException(HttpStatus status, String type, String code, String title) {
        this(status, type, code, title, null);
    }

    private ApiException(HttpStatus status, String type, String code, String title, List<Map<String, String>> errors) {
        super(title);
        this.status = status;
        this.type = type;
        this.code = code;
        this.errors = errors;
    }

    /** 400 validation-failed에 필드별 오류를 담는다. 형식은 Bean Validation 오류와 같다. */
    public static ApiException invalidFields(String code, Map<String, String> fieldMessages) {
        List<Map<String, String>> errors = fieldMessages.entrySet().stream()
                .map(e -> Map.of("field", e.getKey(), "message", e.getValue()))
                .toList();
        return new ApiException(HttpStatus.BAD_REQUEST, "validation-failed", code, "입력값을 확인해 주세요", errors);
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

    public List<Map<String, String>> errors() {
        return errors;
    }
}
