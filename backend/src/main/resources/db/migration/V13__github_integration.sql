-- ============================================================
-- GitHub 이슈트래킹 연동
--  - github_app       : App Manifest flow 로 생성된 GitHub App 자격증명 (단일 행)
--  - project          : 프로젝트별 연결 repo (installation + owner/name)
--  - qa_github_issue  : QA 항목 ↔ GitHub 이슈 매핑
-- ============================================================

-- ─────────────── 1. github_app ───────────────
-- Manifest conversion 응답을 그대로 보관한다. 셀프호스팅 특성상 런타임에 생성되므로 DB 저장.
CREATE TABLE github_app (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    app_id          BIGINT       NOT NULL,
    app_slug        VARCHAR(100) NOT NULL,
    app_name        VARCHAR(200) NULL,
    html_url        VARCHAR(500) NULL,
    client_id       VARCHAR(100) NULL,
    client_secret   VARCHAR(200) NULL,
    webhook_secret  VARCHAR(200) NULL,
    pem             TEXT         NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────── 2. project 에 repo 연결 컬럼 ───────────────
ALTER TABLE project
    ADD COLUMN github_installation_id BIGINT       NULL,
    ADD COLUMN github_repo_owner      VARCHAR(100) NULL,
    ADD COLUMN github_repo_name       VARCHAR(200) NULL;

-- ─────────────── 3. qa_github_issue ───────────────
-- repo 정보를 매핑에도 저장 (프로젝트의 연결 repo 가 나중에 바뀌어도 기존 이슈 추적 유지).
CREATE TABLE qa_github_issue (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    qa_item_id      BIGINT       NOT NULL,
    repo_owner      VARCHAR(100) NOT NULL,
    repo_name       VARCHAR(200) NOT NULL,
    issue_number    INT          NOT NULL,
    issue_url       VARCHAR(500) NOT NULL,
    state           VARCHAR(20)  NOT NULL,        -- open / closed
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_qgi_item (qa_item_id),
    CONSTRAINT fk_qgi_item FOREIGN KEY (qa_item_id) REFERENCES qa_item(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
