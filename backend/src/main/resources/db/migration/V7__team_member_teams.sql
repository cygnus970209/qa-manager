-- Teams 알림 통합:
-- email          : 회사 email. AAD 사용자 조회 (UPN 또는 mail) 의 키.
-- teams_user_id  : AAD Object ID (Graph /users/{email} 응답의 id). 캐시.
-- teams_chat_id  : 봇 user 와의 oneOnOne chat id. 최초 발송 시 생성하고 캐시.
-- teams_notify_enabled : 사용자별 on/off 토글. 기본 ON.

ALTER TABLE team_member
    ADD COLUMN email                VARCHAR(255) NULL AFTER name,
    ADD COLUMN teams_user_id        VARCHAR(64)  NULL AFTER avatar_url,
    ADD COLUMN teams_chat_id        VARCHAR(128) NULL AFTER teams_user_id,
    ADD COLUMN teams_notify_enabled TINYINT(1)   NOT NULL DEFAULT 1 AFTER teams_chat_id,
    ADD UNIQUE KEY uk_team_member_email (email);
