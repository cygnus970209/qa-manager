-- 실행 항목별 플랫폼 (PC / ANDROID / IOS).
-- 런 생성 시 플랫폼을 다중 선택하면 케이스 × 플랫폼으로 실행 항목이 확장된다.
-- NULL 이면 플랫폼 구분 없는 공통 실행(기존 동작).
ALTER TABLE test_run_case
    ADD COLUMN platform VARCHAR(20) NULL AFTER case_id;
