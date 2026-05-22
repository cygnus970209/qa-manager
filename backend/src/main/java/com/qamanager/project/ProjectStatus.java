package com.qamanager.project;

public enum ProjectStatus {
    ACTIVE("active"),
    COMPLETED("completed"),
    PAUSED("paused");

    private final String code;

    ProjectStatus(String code) { this.code = code; }
    public String getCode() { return code; }

    public static ProjectStatus from(String code) {
        for (ProjectStatus s : values()) if (s.code.equals(code)) return s;
        throw new IllegalArgumentException("Unknown project status: " + code);
    }
}
