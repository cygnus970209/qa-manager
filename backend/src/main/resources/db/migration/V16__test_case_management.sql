-- 테스트 케이스 관리 (TestRail 류 + 워크플로우 그래프 기반 케이스 생성)
-- - test_suite     : 프로젝트별 케이스 묶음(폴더 1단계)
-- - test_case      : 프로젝트급 재사용 자산. steps 는 [{action, expected}] JSON.
--                    origin=FLOW 면 test_flow 의 경로에서 생성된 케이스 (flow_stale: 원본 그래프 변경 표시)
-- - test_flow      : 기획 워크플로우 그래프(노드/엣지 JSON 통짜 저장 — 조인/검색 없음)
-- - test_run       : 업데이트(릴리즈)별 실행 1회. 케이스 스냅샷을 담는다.
-- - test_run_case  : 런 × 케이스 실행 항목. 원본 케이스가 수정/삭제돼도 스냅샷은 보존.

CREATE TABLE test_suite (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id  BIGINT       NOT NULL,
    name        VARCHAR(100) NOT NULL,
    sort_order  INT          NOT NULL DEFAULT 0,
    created_at  DATETIME     NOT NULL,
    updated_at  DATETIME     NOT NULL,
    CONSTRAINT fk_test_suite_project FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE,
    KEY idx_test_suite_project (project_id)
);

CREATE TABLE test_flow (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id  BIGINT       NOT NULL,
    update_id   BIGINT       NULL,
    name        VARCHAR(150) NOT NULL,
    graph_json  LONGTEXT     NOT NULL,
    created_at  DATETIME     NOT NULL,
    updated_at  DATETIME     NOT NULL,
    CONSTRAINT fk_test_flow_project FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE,
    CONSTRAINT fk_test_flow_update FOREIGN KEY (update_id) REFERENCES project_update (id) ON DELETE SET NULL,
    KEY idx_test_flow_project (project_id)
);

CREATE TABLE test_case (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id    BIGINT       NOT NULL,
    suite_id      BIGINT       NULL,
    title         VARCHAR(200) NOT NULL,
    precondition  TEXT         NULL,
    steps_json    LONGTEXT     NOT NULL,
    priority      VARCHAR(20)  NOT NULL DEFAULT 'medium',
    origin        VARCHAR(20)  NOT NULL DEFAULT 'MANUAL',
    flow_id       BIGINT       NULL,
    flow_stale    TINYINT(1)   NOT NULL DEFAULT 0,
    created_at    DATETIME     NOT NULL,
    updated_at    DATETIME     NOT NULL,
    CONSTRAINT fk_test_case_project FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE,
    CONSTRAINT fk_test_case_suite FOREIGN KEY (suite_id) REFERENCES test_suite (id) ON DELETE SET NULL,
    CONSTRAINT fk_test_case_flow FOREIGN KEY (flow_id) REFERENCES test_flow (id) ON DELETE SET NULL,
    KEY idx_test_case_project (project_id),
    KEY idx_test_case_suite (suite_id)
);

CREATE TABLE test_run (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    update_id   BIGINT       NOT NULL,
    name        VARCHAR(150) NOT NULL,
    closed_at   DATETIME     NULL,
    created_at  DATETIME     NOT NULL,
    updated_at  DATETIME     NOT NULL,
    CONSTRAINT fk_test_run_update FOREIGN KEY (update_id) REFERENCES project_update (id) ON DELETE CASCADE,
    KEY idx_test_run_update (update_id)
);

CREATE TABLE test_run_case (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    run_id        BIGINT       NOT NULL,
    case_id       BIGINT       NULL,
    sort_order    INT          NOT NULL DEFAULT 0,
    title         VARCHAR(200) NOT NULL,
    precondition  TEXT         NULL,
    steps_json    LONGTEXT     NOT NULL,
    priority      VARCHAR(20)  NOT NULL,
    result        VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    note          TEXT         NULL,
    qa_item_id    BIGINT       NULL,
    executed_at   DATETIME     NULL,
    created_at    DATETIME     NOT NULL,
    updated_at    DATETIME     NOT NULL,
    CONSTRAINT fk_test_run_case_run FOREIGN KEY (run_id) REFERENCES test_run (id) ON DELETE CASCADE,
    CONSTRAINT fk_test_run_case_case FOREIGN KEY (case_id) REFERENCES test_case (id) ON DELETE SET NULL,
    CONSTRAINT fk_test_run_case_qa FOREIGN KEY (qa_item_id) REFERENCES qa_item (id) ON DELETE SET NULL,
    KEY idx_test_run_case_run (run_id)
);
