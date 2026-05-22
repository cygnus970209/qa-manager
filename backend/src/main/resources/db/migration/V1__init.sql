-- ============================================================
-- QA Manager - 초기 스키마 (MariaDB 10.6+)
-- 모든 테이블은 InnoDB + utf8mb4_unicode_ci
-- ID는 BIGINT auto_increment
-- ============================================================

-- ─────────────── 1. team_member ───────────────
CREATE TABLE team_member (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    username        VARCHAR(50)  NOT NULL,
    password_hash   VARCHAR(100) NOT NULL,
    name            VARCHAR(50)  NOT NULL,
    role            VARCHAR(50)  NULL,
    avatar_url      VARCHAR(500) NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_team_member_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────── 2. project ───────────────
CREATE TABLE project (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    name            VARCHAR(100) NOT NULL,
    description     TEXT         NULL,
    status          VARCHAR(20)  NOT NULL,        -- active / completed / paused
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY ix_project_status (status),
    KEY ix_project_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────── 3. project_pin (사용자별 즐겨찾기) ───────────────
CREATE TABLE project_pin (
    project_id      BIGINT      NOT NULL,
    member_id       BIGINT      NOT NULL,
    created_at      DATETIME(6) NOT NULL,
    PRIMARY KEY (project_id, member_id),
    CONSTRAINT fk_pin_project FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE,
    CONSTRAINT fk_pin_member  FOREIGN KEY (member_id)  REFERENCES team_member(id) ON DELETE CASCADE,
    KEY ix_pin_member (member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────── 4. project_update (예약어라 _update 사용) ───────────────
CREATE TABLE project_update (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    project_id      BIGINT       NOT NULL,
    version         VARCHAR(50)  NOT NULL,
    title           VARCHAR(200) NOT NULL,
    description     TEXT         NULL,
    status          VARCHAR(20)  NOT NULL,        -- in_progress / testing / released
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_update_project FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE,
    KEY ix_update_project_id (project_id),
    KEY ix_update_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────── 5. qa_item ───────────────
CREATE TABLE qa_item (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    update_id       BIGINT       NOT NULL,
    title           VARCHAR(200) NOT NULL,
    description     TEXT         NULL,
    category        VARCHAR(50)  NULL,
    status          VARCHAR(20)  NOT NULL,        -- pending / in_progress / resolved / closed
    assignee_id     BIGINT       NULL,
    priority        VARCHAR(20)  NOT NULL,        -- low / medium / high / critical
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_qa_update   FOREIGN KEY (update_id)   REFERENCES project_update(id) ON DELETE CASCADE,
    CONSTRAINT fk_qa_assignee FOREIGN KEY (assignee_id) REFERENCES team_member(id)    ON DELETE SET NULL,
    KEY ix_qa_update_id (update_id),
    KEY ix_qa_assignee_id (assignee_id),
    KEY ix_qa_status (status),
    KEY ix_qa_priority (priority)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────── 6. qa_item_image ───────────────
CREATE TABLE qa_item_image (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    qa_item_id      BIGINT       NOT NULL,
    image_url       VARCHAR(800) NOT NULL,
    sort_order      INT          NOT NULL DEFAULT 0,
    created_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_qa_img_item FOREIGN KEY (qa_item_id) REFERENCES qa_item(id) ON DELETE CASCADE,
    KEY ix_qa_img_item (qa_item_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────── 7. qa_history (변경 이력) ───────────────
CREATE TABLE qa_history (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    qa_item_id      BIGINT       NOT NULL,
    field           VARCHAR(50)  NOT NULL,
    old_value       VARCHAR(500) NULL,
    new_value       VARCHAR(500) NULL,
    changed_by_id   BIGINT       NULL,
    changed_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_qh_item    FOREIGN KEY (qa_item_id)    REFERENCES qa_item(id)     ON DELETE CASCADE,
    CONSTRAINT fk_qh_member  FOREIGN KEY (changed_by_id) REFERENCES team_member(id) ON DELETE SET NULL,
    KEY ix_qh_item (qa_item_id, changed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────── 8. qa_comment ───────────────
CREATE TABLE qa_comment (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    qa_item_id      BIGINT       NOT NULL,
    parent_id       BIGINT       NULL,
    author_id       BIGINT       NOT NULL,
    content         TEXT         NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_qc_item   FOREIGN KEY (qa_item_id) REFERENCES qa_item(id)     ON DELETE CASCADE,
    CONSTRAINT fk_qc_parent FOREIGN KEY (parent_id)  REFERENCES qa_comment(id)  ON DELETE CASCADE,
    CONSTRAINT fk_qc_author FOREIGN KEY (author_id)  REFERENCES team_member(id) ON DELETE RESTRICT,
    KEY ix_qc_item (qa_item_id, created_at),
    KEY ix_qc_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────── 9. qa_comment_image ───────────────
CREATE TABLE qa_comment_image (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    comment_id      BIGINT       NOT NULL,
    image_url       VARCHAR(800) NOT NULL,
    sort_order      INT          NOT NULL DEFAULT 0,
    created_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_qci_comment FOREIGN KEY (comment_id) REFERENCES qa_comment(id) ON DELETE CASCADE,
    KEY ix_qci_comment (comment_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────── 10. qa_comment_reaction (이모지 반응) ───────────────
CREATE TABLE qa_comment_reaction (
    comment_id      BIGINT       NOT NULL,
    emoji           VARCHAR(20)  NOT NULL,
    member_id       BIGINT       NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (comment_id, emoji, member_id),
    CONSTRAINT fk_qcr_comment FOREIGN KEY (comment_id) REFERENCES qa_comment(id)  ON DELETE CASCADE,
    CONSTRAINT fk_qcr_member  FOREIGN KEY (member_id)  REFERENCES team_member(id) ON DELETE CASCADE,
    KEY ix_qcr_member (member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────── 11. notification ───────────────
CREATE TABLE notification (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    recipient_id    BIGINT       NOT NULL,
    actor_id        BIGINT       NULL,
    type            VARCHAR(20)  NOT NULL,        -- qa / update / project / mention
    message         VARCHAR(500) NOT NULL,
    project_id      BIGINT       NULL,
    qa_item_id      BIGINT       NULL,
    is_read         TINYINT(1)   NOT NULL DEFAULT 0,
    created_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_nt_recipient FOREIGN KEY (recipient_id) REFERENCES team_member(id) ON DELETE CASCADE,
    CONSTRAINT fk_nt_actor     FOREIGN KEY (actor_id)     REFERENCES team_member(id) ON DELETE SET NULL,
    CONSTRAINT fk_nt_project   FOREIGN KEY (project_id)   REFERENCES project(id)     ON DELETE SET NULL,
    CONSTRAINT fk_nt_qa        FOREIGN KEY (qa_item_id)   REFERENCES qa_item(id)     ON DELETE SET NULL,
    KEY ix_nt_recipient (recipient_id, is_read, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
