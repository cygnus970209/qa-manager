package com.qamanager.qa.shared;

public enum QaPriority {
    LOW("low", "낮음"),
    MEDIUM("medium", "보통"),
    HIGH("high", "높음"),
    CRITICAL("critical", "긴급");

    private final String code;
    private final String label;
    QaPriority(String code, String label) { this.code = code; this.label = label; }
    public String getCode() { return code; }
    public String getLabel() { return label; }

    public static QaPriority from(String code) {
        for (QaPriority p : values()) if (p.code.equals(code)) return p;
        throw new IllegalArgumentException("Unknown qa priority: " + code);
    }

    /** code(영어) -> 한글 라벨. 알 수 없는 code 는 원본 반환. */
    public static String labelOf(String code) {
        for (QaPriority p : values()) if (p.code.equals(code)) return p.label;
        return code;
    }
}
