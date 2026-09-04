-- 사용자별 프로젝트 순서 (사이드바 "프로젝트 순서 변경").
-- 순서를 저장한 프로젝트만 행이 있고, 행이 없는 프로젝트는 기본 정렬(생성일 desc) 뒤에 붙는다.
-- 정렬은 항상 고정(project_pin) 우선 → 이 순서 → 생성일 desc.
CREATE TABLE project_member_order (
    member_id       BIGINT  NOT NULL,
    project_id      BIGINT  NOT NULL,
    sort_order      INT     NOT NULL,
    PRIMARY KEY (member_id, project_id),
    CONSTRAINT fk_pmo_member  FOREIGN KEY (member_id)  REFERENCES team_member(id) ON DELETE CASCADE,
    CONSTRAINT fk_pmo_project FOREIGN KEY (project_id) REFERENCES project(id)     ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
