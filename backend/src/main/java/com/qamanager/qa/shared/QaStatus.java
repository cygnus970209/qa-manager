package com.qamanager.qa.shared;

public enum QaStatus {
    PENDING("pending"),
    IN_PROGRESS("in_progress"),
    RESOLVED("resolved"),
    CLOSED("closed");

    private final String code;
    QaStatus(String code) { this.code = code; }
    public String getCode() { return code; }

    public static QaStatus from(String code) {
        for (QaStatus s : values()) if (s.code.equals(code)) return s;
        throw new IllegalArgumentException("Unknown qa status: " + code);
    }
}
