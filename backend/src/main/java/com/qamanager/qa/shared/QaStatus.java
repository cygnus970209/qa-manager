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
    NEEDS_FIX("needs_fix", "수정필요"),
    IN_PROGRESS("in_progress", "진행중"),
    FIX_DONE("fix_done", "수정완료"),
    CONFIRMED("confirmed", "확인완료"),
    ON_HOLD("on_hold", "보류"),
    NEEDS_RECHECK("needs_recheck", "추가확인필요");

    private final String code;
    private final String label;
    QaStatus(String code, String label) { this.code = code; this.label = label; }
    public String getCode() { return code; }
    public String getLabel() { return label; }

    public static QaStatus from(String code) {
        for (QaStatus s : values()) if (s.code.equals(code)) return s;
        throw new IllegalArgumentException("Unknown qa status: " + code);
    }

    /** code(영어) -> 한글 라벨. 알림/메세지 문구용. 알 수 없는 code 는 원본 반환. */
    public static String labelOf(String code) {
        for (QaStatus s : values()) if (s.code.equals(code)) return s.label;
        return code;
    }
}
