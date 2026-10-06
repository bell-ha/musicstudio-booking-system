package com.musicstudio.common.error;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;

/**
 * 업무 규칙 위반을 Problem Details 응답으로 바꾸기 위한 예외.
 * type은 docs/architecture/02-API.md 1절의 오류 종류, code는 세부 원인이다.
 * 응답에 더 실을 값(필드 오류, 충돌한 예약 등)은 with()로 붙인다.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String type;
    private final String code;
    private final Map<String, Object> properties = new LinkedHashMap<>();

    public ApiException(HttpStatus status, String type, String code, String title) {
        // 업무 규칙 위반이라 스택 트레이스가 필요 없다. 시간표는 칸마다 규칙 예외를 만들고 잡으므로 비용도 줄인다.
        super(title, null, false, false);
        this.status = status;
        this.type = type;
        this.code = code;
    }

    public ApiException with(String key, Object value) {
        properties.put(key, value);
        return this;
    }

    /** 400 validation-failed에 필드별 오류를 담는다. 형식은 Bean Validation 오류와 같다. */
    public static ApiException invalidFields(String code, Map<String, String> fieldMessages) {
        List<Map<String, String>> errors = fieldMessages.entrySet().stream()
                .map(e -> Map.of("field", e.getKey(), "message", e.getValue()))
                .toList();
        return invalid(code, "입력값을 확인해 주세요").with("errors", errors);
    }

    public static ApiException conflict(String code, String title) {
        return new ApiException(HttpStatus.CONFLICT, "conflict", code, title);
    }

    public static ApiException invalid(String code, String title) {
        return new ApiException(HttpStatus.BAD_REQUEST, "validation-failed", code, title);
    }

    public static ApiException notFound(String title) {
        return new ApiException(HttpStatus.NOT_FOUND, "not-found", "NOT_FOUND", title);
    }

    public static ApiException policyViolation(String code, String title) {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "policy-violation", code, title);
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

    public Map<String, Object> properties() {
        return properties;
    }
}
