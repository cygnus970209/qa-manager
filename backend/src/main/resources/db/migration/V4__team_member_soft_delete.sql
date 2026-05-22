-- 멤버 소프트 삭제 도입.
-- deleted_at IS NULL = 활성, NOT NULL = 비활성(소프트 삭제).
-- 참조하는 댓글/이력/알림 등은 그대로 보존되며, 로그인은 차단된다.

ALTER TABLE team_member
    ADD COLUMN deleted_at TIMESTAMP(6) NULL;

CREATE INDEX idx_team_member_deleted_at ON team_member (deleted_at);
