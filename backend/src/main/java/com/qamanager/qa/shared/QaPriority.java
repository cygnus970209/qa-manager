package com.qamanager.qa.shared;

public enum QaPriority {
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high"),
    CRITICAL("critical");

    private final String code;
    QaPriority(String code) { this.code = code; }
    public String getCode() { return code; }

    public static QaPriority from(String code) {
        for (QaPriority p : values()) if (p.code.equals(code)) return p;
        throw new IllegalArgumentException("Unknown qa priority: " + code);
    }
}
