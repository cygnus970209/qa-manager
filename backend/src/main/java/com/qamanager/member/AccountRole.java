package com.qamanager.member;

/**
 * 계정 권한. 직무 표기용 자유 텍스트인 {@link TeamMember#getRole()} 와는 별개의 접근 제어 개념.
 * - ADMIN  : 관리자 페이지 접근, 팀원 관리(생성/수정/삭제/비번초기화/권한 부여), 전역 연동 설정 변경
 * - MEMBER : 일반 사용 (프로젝트/업데이트/QA/코멘트 협업 기능 전체)
 */
public enum AccountRole {
    ADMIN,
    MEMBER
}
