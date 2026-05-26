-- QA 담당자 구조 확장: 단일 assignee → tester(작성자) + assignee1 + assignee2.
-- 기존 assignee_id 는 assignee1_id 로 보존. tester/assignee2 는 NULL 로 시작.

-- 1) 기존 FK / 인덱스 제거
ALTER TABLE qa_item DROP FOREIGN KEY fk_qa_assignee;
ALTER TABLE qa_item DROP INDEX ix_qa_assignee_id;

-- 2) 기존 컬럼 rename
ALTER TABLE qa_item RENAME COLUMN assignee_id TO assignee1_id;

-- 3) 신규 컬럼 추가
ALTER TABLE qa_item
    ADD COLUMN assignee2_id BIGINT NULL AFTER assignee1_id,
    ADD COLUMN tester_id    BIGINT NULL AFTER status;

-- 4) FK / 인덱스 재설정
ALTER TABLE qa_item
    ADD CONSTRAINT fk_qa_assignee1 FOREIGN KEY (assignee1_id) REFERENCES team_member(id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_qa_assignee2 FOREIGN KEY (assignee2_id) REFERENCES team_member(id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_qa_tester    FOREIGN KEY (tester_id)    REFERENCES team_member(id) ON DELETE SET NULL,
    ADD INDEX ix_qa_assignee1_id (assignee1_id),
    ADD INDEX ix_qa_assignee2_id (assignee2_id),
    ADD INDEX ix_qa_tester_id    (tester_id);
