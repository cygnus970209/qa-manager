package com.qamanager.common;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.Map;

@Getter
public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    /** 응답 body 에 함께 실을 부가 정보(없으면 null). */
    private final Object details;

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, null);
    }

    public ApiException(HttpStatus status, String code, String message, Object details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details;
    }

    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    public static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, "CONFLICT", message);
    }

    public static ApiException unauthorized(String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    public static ApiException tooManyRequests(String message) {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS", message);
    }

    /** 로그인 OTP 코드 불일치. 남은 시도 횟수를 details 로 전달. */
    public static ApiException otpInvalid(String message, int remainingAttempts) {
        return new ApiException(HttpStatus.UNAUTHORIZED, "OTP_INVALID", message,
            Map.of("remainingAttempts", remainingAttempts));
    }
}
