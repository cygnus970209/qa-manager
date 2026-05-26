-- QA 상태 4단계 → 6단계 마이그레이션.
-- 코드 변경: pending→needs_fix, resolved→fix_done, closed→confirmed (in_progress 유지)
-- 신규: on_hold, needs_recheck (기존 데이터에는 매핑되지 않음, 향후 사용)

-- 1) qa_item.status 코드 변환
UPDATE qa_item SET status = 'needs_fix' WHERE status = 'pending';
UPDATE qa_item SET status = 'fix_done'  WHERE status = 'resolved';
UPDATE qa_item SET status = 'confirmed' WHERE status = 'closed';

-- 2) qa_history 의 status 변경 이력도 동일 매핑 (field='status' 인 행)
UPDATE qa_history SET old_value = 'needs_fix' WHERE field = 'status' AND old_value = 'pending';
UPDATE qa_history SET new_value = 'needs_fix' WHERE field = 'status' AND new_value = 'pending';
UPDATE qa_history SET old_value = 'fix_done'  WHERE field = 'status' AND old_value = 'resolved';
UPDATE qa_history SET new_value = 'fix_done'  WHERE field = 'status' AND new_value = 'resolved';
UPDATE qa_history SET old_value = 'confirmed' WHERE field = 'status' AND old_value = 'closed';
UPDATE qa_history SET new_value = 'confirmed' WHERE field = 'status' AND new_value = 'closed';
