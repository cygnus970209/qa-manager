-- 계정 권한(account_role): ADMIN / MEMBER.
-- - ADMIN 만 관리자 페이지 접근 + 팀원 관리(생성/수정/삭제/비번초기화/권한 부여) + 전역 연동 설정 변경 가능.
-- - 기존 설치본은 모든 멤버가 관리 기능을 쓰던 상태였으므로 전원 ADMIN 으로 백필해
--   배포 직후 관리자 0명 잠금을 방지한다. 이후 관리자가 팀원 관리 탭에서 조정.
-- - 이 마이그레이션 이후 새로 생성되는 멤버는 기본 MEMBER.
ALTER TABLE team_member
    ADD COLUMN account_role VARCHAR(20) NOT NULL DEFAULT 'MEMBER' AFTER role;

UPDATE team_member SET account_role = 'ADMIN';
