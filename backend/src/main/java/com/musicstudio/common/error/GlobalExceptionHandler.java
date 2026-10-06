package com.musicstudio.common.error;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * 모든 오류를 RFC 9457 Problem Details로 응답한다.
 * 프론트가 title을 그대로 보여 주므로 내부 메시지나 영어 문장을 내보내지 않는다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ProblemDetail handleApi(ApiException e) {
        ProblemDetail problem = ProblemTypes.of(e.status(), e.type(), e.getMessage());
        problem.setProperty("code", e.code());
        e.properties().forEach(problem::setProperty);
        return problem;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception e) {
        log.error("처리하지 못한 예외", e);
        return ProblemTypes.of(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "일시적인 오류가 발생했습니다");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemTypes.of(HttpStatus.BAD_REQUEST, "validation-failed", "입력값을 확인해 주세요");
        List<Map<String, String>> errors = e.getBindingResult().getFieldErrors().stream()
                .map(f -> Map.of("field", f.getField(), "message", String.valueOf(f.getDefaultMessage())))
                .toList();
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    /** Spring이 처리하는 나머지 예외(읽을 수 없는 JSON, 없는 enum 값 등)도 같은 형식으로 바꾼다. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception e, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = status.value() == 400
                ? ProblemTypes.of(status, "validation-failed", "요청 형식이 올바르지 않습니다")
                : ProblemTypes.of(status, "request-failed", "요청을 처리할 수 없습니다");
        return ResponseEntity.status(status).headers(headers).body(problem);
    }
}
