package com.qamanager.notification.teams;

/** Teams/Graph/Bot Connector API 호출 실패. 발송 경로에서 잡아 로그 후 swallow. */
public class TeamsApiException extends RuntimeException {

    /** 봇이 사용자 personal scope 에 설치되지 않았거나 차단된 경우 (Bot Connector 403). */
    private final boolean botNotInstalled;

    public TeamsApiException(String message) {
        this(message, null, false);
    }

    public TeamsApiException(String message, Throwable cause) {
        this(message, cause, false);
    }

    public TeamsApiException(String message, Throwable cause, boolean botNotInstalled) {
        super(message, cause);
        this.botNotInstalled = botNotInstalled;
    }

    public boolean isBotNotInstalled() {
        return botNotInstalled;
    }
}
