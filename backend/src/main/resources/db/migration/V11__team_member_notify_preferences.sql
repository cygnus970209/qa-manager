-- 멤버별 알림 개인화 (Teams 발송에만 적용).
-- notify_*_enabled : 알림 종류(qa/comment/reply)별 on/off. 기본 ON.
-- quiet_hours_*    : 방해금지 시간대 "HH:mm" (서버 Asia/Seoul 기준). NULL = 미설정. 구간 내 발송은 스킵.

ALTER TABLE team_member
    ADD COLUMN notify_qa_enabled      TINYINT(1)  NOT NULL DEFAULT 1 AFTER teams_notify_enabled,
    ADD COLUMN notify_comment_enabled TINYINT(1)  NOT NULL DEFAULT 1 AFTER notify_qa_enabled,
    ADD COLUMN notify_reply_enabled   TINYINT(1)  NOT NULL DEFAULT 1 AFTER notify_comment_enabled,
    ADD COLUMN quiet_hours_start      VARCHAR(5)  NULL AFTER notify_reply_enabled,
    ADD COLUMN quiet_hours_end        VARCHAR(5)  NULL AFTER quiet_hours_start;
