-- API 요청 로그 (변경 + auth 관련 GET 만 적재) / Teams 발송 로그.
-- 비동기 적재이므로 main 요청 트랜잭션과 분리. member_id 는 FK 두지 않는다 (멤버 삭제와 무관하게 로그 보존).

CREATE TABLE api_request_log (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    method        VARCHAR(10)  NOT NULL,
    path          VARCHAR(500) NOT NULL,
    query_string  VARCHAR(1000) NULL,
    status        INT          NULL,
    member_id     BIGINT       NULL,
    username      VARCHAR(50)  NULL,
    ip            VARCHAR(64)  NULL,
    user_agent    VARCHAR(500) NULL,
    duration_ms   INT          NULL,
    error_message VARCHAR(500) NULL,
    created_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY ix_api_log_created_at (created_at),
    KEY ix_api_log_member_id (member_id),
    KEY ix_api_log_path (path(191)),
    KEY ix_api_log_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE teams_send_log (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    -- 'sent' / 'skipped' / 'failed'
    status          VARCHAR(16)  NOT NULL,
    -- 알림 종류: 'qa' / 'comment' / 'reply' / 'test' / 'system' 등 NotificationPayload.type
    notification_type VARCHAR(20) NULL,
    recipient_id    BIGINT       NULL,
    recipient_email VARCHAR(255) NULL,
    message         VARCHAR(500) NULL,
    -- skip 사유 또는 실패 메세지
    detail          VARCHAR(1000) NULL,
    duration_ms     INT          NULL,
    created_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY ix_teams_log_created_at (created_at),
    KEY ix_teams_log_status (status),
    KEY ix_teams_log_recipient_id (recipient_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
