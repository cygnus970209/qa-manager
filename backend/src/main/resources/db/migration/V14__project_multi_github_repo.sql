-- ============================================================
-- 프로젝트 GitHub repo 다중 연결
--  - project_github_repo : 프로젝트 1 : repo N 연결 테이블
--  - 기존 project 의 단일 repo 컬럼 데이터를 이관 후 제거
-- ============================================================

CREATE TABLE project_github_repo (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    project_id      BIGINT       NOT NULL,
    installation_id BIGINT       NOT NULL,
    repo_owner      VARCHAR(100) NOT NULL,
    repo_name       VARCHAR(200) NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_pgr_project_repo (project_id, repo_owner, repo_name),
    CONSTRAINT fk_pgr_project FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 기존 단일 연결 데이터 이관
INSERT INTO project_github_repo (project_id, installation_id, repo_owner, repo_name, created_at, updated_at)
SELECT id, github_installation_id, github_repo_owner, github_repo_name, NOW(6), NOW(6)
FROM project
WHERE github_installation_id IS NOT NULL
  AND github_repo_owner IS NOT NULL
  AND github_repo_name IS NOT NULL;

ALTER TABLE project
    DROP COLUMN github_installation_id,
    DROP COLUMN github_repo_owner,
    DROP COLUMN github_repo_name;
