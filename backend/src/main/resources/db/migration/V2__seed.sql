-- ============================================================
-- 데모 시드 데이터.
-- 비밀번호는 원본 1234. BCrypt 해시는 각 계정마다 별도 salt.
-- 운영 환경에서는 V2를 비활성화하거나 별도 프로파일에서만 실행.
-- ============================================================

-- ─────────────── team_member ───────────────
INSERT INTO team_member (username, password_hash, name, role, avatar_url, created_at, updated_at) VALUES
('kimminjun',    '$2a$10$XDw.4TMu61GRZQZaagpBO./QDGwXTGB.q5Xgmrt3xjd1Ifg3pFiRC', '김민준', 'FE 개발자',  'https://api.dicebear.com/7.x/avataaars/svg?seed=kim',   NOW(6), NOW(6)),
('parkseoyeon',  '$2a$10$lKGJU2v7oP.D58VNLxApdu9yEYZRGZCSq.ISnjPMkwh9Iq5V79DQe', '박서연', 'BE 개발자',  'https://api.dicebear.com/7.x/avataaars/svg?seed=park',  NOW(6), NOW(6)),
('leedoyoon',    '$2a$10$.IRn1ZVhbKzPajTYDyhjE.o1GEo5QMF4MVS/eGIgX13HFdfunvK3e', '이도윤', 'DevOps',     'https://api.dicebear.com/7.x/avataaars/svg?seed=lee',   NOW(6), NOW(6)),
('choijiu',      '$2a$10$MXCzhzGxbcjXkkh7Vr/R2eMaOd9JGXRNBvBCIBCoG1it6D0pX1P1W', '최지우', 'QA 엔지니어','https://api.dicebear.com/7.x/avataaars/svg?seed=choi',  NOW(6), NOW(6)),
('jeonghyunwoo', '$2a$10$6mMc4Ehg8ynvXMf9CpTSR.89I8pvz/MO07NRcyCwE1eN4/Ivoyu3i', '정현우', '풀스택',     'https://api.dicebear.com/7.x/avataaars/svg?seed=jeong', NOW(6), NOW(6));

-- ─────────────── project ───────────────
INSERT INTO project (name, description, status, created_at, updated_at) VALUES
('모바일 쇼핑몰 앱',  'iOS/Android 하이브리드 쇼핑몰 앱 리뉴얼 프로젝트. 신규 결제 시스템 도입 및 UI/UX 개선.', 'active',    '2026-01-15 00:00:00.000000', '2026-01-15 00:00:00.000000'),
('관리자 대시보드',   '내부 운영팀을 위한 실시간 데이터 분석 및 모니터링 대시보드 구축.',                          'active',    '2026-02-20 00:00:00.000000', '2026-02-20 00:00:00.000000'),
('SNS 연동 API',     '카카오톡, 인스타그램, 네이버 등 외부 SNS 플랫폼 연동 API 개발.',                              'paused',    '2026-03-10 00:00:00.000000', '2026-03-10 00:00:00.000000'),
('회원 시스템 v2',   '기존 회원 인증 시스템 전면 개편. OAuth2 + MFA 다중 인증 도입.',                              'completed', '2025-11-01 00:00:00.000000', '2025-11-01 00:00:00.000000');
