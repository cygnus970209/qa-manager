package com.qamanager.notification.teams;

/** Teams/Graph API 호출 실패. 발송 경로에서 잡아 로그 후 swallow. */
public class TeamsApiException extends RuntimeException {
    public TeamsApiException(String message) { super(message); }
    public TeamsApiException(String message, Throwable cause) { super(message, cause); }
}
