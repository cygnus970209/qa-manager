package com.qamanager.qa.shared;

/**
 * QA 항목 상태.
 * - NEEDS_FIX     수정필요   (개발자 작업 대기)
 * - IN_PROGRESS   진행중     (개발자 작업 중)
 * - FIX_DONE      수정완료   (개발자 끝, 테스터 확인 대기)
 * - CONFIRMED     확인완료   (테스터 확인 끝, 종료)
 * - ON_HOLD       보류
 * - NEEDS_RECHECK 추가확인필요
 */
public enum QaStatus {
    NEEDS_FIX("needs_fix"),
    IN_PROGRESS("in_progress"),
    FIX_DONE("fix_done"),
    CONFIRMED("confirmed"),
    ON_HOLD("on_hold"),
    NEEDS_RECHECK("needs_recheck");

    private final String code;
    QaStatus(String code) { this.code = code; }
    public String getCode() { return code; }

    public static QaStatus from(String code) {
        for (QaStatus s : values()) if (s.code.equals(code)) return s;
        throw new IllegalArgumentException("Unknown qa status: " + code);
    }
}
