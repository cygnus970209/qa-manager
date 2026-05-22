package com.qamanager.projectupdate;

public enum UpdateStatus {
    IN_PROGRESS("in_progress"),
    TESTING("testing"),
    RELEASED("released");

    private final String code;

    UpdateStatus(String code) { this.code = code; }
    public String getCode() { return code; }

    public static UpdateStatus from(String code) {
        for (UpdateStatus s : values()) if (s.code.equals(code)) return s;
        throw new IllegalArgumentException("Unknown update status: " + code);
    }
}
