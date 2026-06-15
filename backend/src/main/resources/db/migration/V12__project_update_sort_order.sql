-- project_update 수동 정렬용 sort_order 컬럼 추가
ALTER TABLE project_update ADD COLUMN sort_order INT NOT NULL DEFAULT 0 AFTER status;

-- 기존 데이터는 현재 노출 순서(created_at DESC)를 초기값으로 부여
UPDATE project_update pu
JOIN (
    SELECT id,
           ROW_NUMBER() OVER (PARTITION BY project_id ORDER BY created_at DESC, id DESC) - 1 AS rn
    FROM project_update
) t ON t.id = pu.id
SET pu.sort_order = t.rn;
