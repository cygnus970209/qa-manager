import type { DemoState } from './types'

/**
 * 데모 초기 상태. 백엔드 시드(V2/V3)를 현재 프론트 타입(6-state, tester/assignee1/2)에 맞춰 재작성.
 * 매 호출마다 새 객체를 만들어 반환하므로 reset 시 안전하게 재사용된다.
 * 비밀번호는 데모 안내용 평문(전부 1234) — 실제 인증이 아니므로 해시하지 않는다.
 */
export function createSeed(): DemoState {
  return {
    members: [
      { id: 1, username: 'kimminjun', password: '1234', name: '김민준', role: 'FE 개발자', email: null, avatarUrl: 'https://api.dicebear.com/7.x/avataaars/svg?seed=kim' },
      { id: 2, username: 'parkseoyeon', password: '1234', name: '박서연', role: 'BE 개발자', email: null, avatarUrl: 'https://api.dicebear.com/7.x/avataaars/svg?seed=park' },
      { id: 3, username: 'leedoyoon', password: '1234', name: '이도윤', role: 'DevOps', email: null, avatarUrl: 'https://api.dicebear.com/7.x/avataaars/svg?seed=lee' },
      { id: 4, username: 'choijiu', password: '1234', name: '최지우', role: 'QA 엔지니어', email: null, avatarUrl: 'https://api.dicebear.com/7.x/avataaars/svg?seed=choi' },
      { id: 5, username: 'jeonghyunwoo', password: '1234', name: '정현우', role: '풀스택', email: null, avatarUrl: 'https://api.dicebear.com/7.x/avataaars/svg?seed=jeong' },
    ],

    projects: [
      { id: 1, name: '모바일 쇼핑몰 앱', description: 'iOS/Android 하이브리드 쇼핑몰 앱 리뉴얼 프로젝트. 신규 결제 시스템 도입 및 UI/UX 개선.', status: 'active', pinnedBy: [], githubInstallationId: 9001, githubRepoOwner: 'qa-demo-org', githubRepoName: 'mobile-shop-app', createdAt: '2026-01-15T00:00:00', updatedAt: '2026-01-15T00:00:00' },
      { id: 2, name: '관리자 대시보드', description: '내부 운영팀을 위한 실시간 데이터 분석 및 모니터링 대시보드 구축.', status: 'active', pinnedBy: [], githubInstallationId: 9001, githubRepoOwner: 'qa-demo-org', githubRepoName: 'admin-dashboard', createdAt: '2026-02-20T00:00:00', updatedAt: '2026-02-20T00:00:00' },
      { id: 3, name: 'SNS 연동 API', description: '카카오톡, 인스타그램, 네이버 등 외부 SNS 플랫폼 연동 API 개발.', status: 'paused', pinnedBy: [], githubInstallationId: null, githubRepoOwner: null, githubRepoName: null, createdAt: '2026-03-10T00:00:00', updatedAt: '2026-03-10T00:00:00' },
      { id: 4, name: '회원 시스템 v2', description: '기존 회원 인증 시스템 전면 개편. OAuth2 + MFA 다중 인증 도입.', status: 'completed', pinnedBy: [], githubInstallationId: null, githubRepoOwner: null, githubRepoName: null, createdAt: '2025-11-01T00:00:00', updatedAt: '2025-11-01T00:00:00' },
    ],

    updates: [
      { id: 1, projectId: 1, version: 'v2.3.0', title: '결제 모듈 리뉴얼', description: '기존 PG사 연동 방식 개선 및 신규 간편결제 수단 추가.', status: 'testing', sortOrder: 0, createdAt: '2026-04-01T00:00:00', updatedAt: '2026-04-01T00:00:00' },
      { id: 2, projectId: 1, version: 'v2.3.1', title: '푸시 알림 기능 강화', description: 'A/B 테스트 기반 푸시 알림 개인화 및 딥링크 처리.', status: 'in_progress', sortOrder: 1, createdAt: '2026-04-20T00:00:00', updatedAt: '2026-04-20T00:00:00' },
      { id: 3, projectId: 1, version: 'v2.2.5', title: '장바구니 버그 수정', description: '장바구니 수량 변경 시 가격 미갱신 이슈 수정.', status: 'released', sortOrder: 2, createdAt: '2026-03-15T00:00:00', updatedAt: '2026-03-15T00:00:00' },
      { id: 4, projectId: 2, version: 'v1.0.0', title: '초기 대시보드 릴리스', description: '핵심 KPI 위젯 및 실시간 데이터 스트리밍 구현.', status: 'released', sortOrder: 0, createdAt: '2026-05-01T00:00:00', updatedAt: '2026-05-01T00:00:00' },
      { id: 5, projectId: 2, version: 'v1.1.0', title: 'AI 이상탐지 위젯 추가', description: '자동화된 이상징후 탐지 알림 위젯 도입.', status: 'in_progress', sortOrder: 1, createdAt: '2026-05-10T00:00:00', updatedAt: '2026-05-10T00:00:00' },
      { id: 6, projectId: 3, version: 'v0.9.0-beta', title: '카카오톡 연동 베타', description: '카카오톡 메시지 API 연동 베타 테스트.', status: 'testing', sortOrder: 0, createdAt: '2026-03-20T00:00:00', updatedAt: '2026-03-20T00:00:00' },
      { id: 7, projectId: 4, version: 'v2.0.0', title: 'MFA 도입', description: 'Google Authenticator 기반 다중 인증 도입.', status: 'released', sortOrder: 0, createdAt: '2025-12-01T00:00:00', updatedAt: '2025-12-01T00:00:00' },
    ],

    qa: [
      { id: 1, updateId: 1, title: '카드 결제 시 오류 메시지 미표시', description: '유효하지 않은 카드 번호 입력 시, 오류 메시지가 노출되지 않고 로딩 인디케이터만 무한 회전.', category: '결제', status: 'confirmed', testerId: 4, assignee1Id: 1, assignee2Id: null, priority: 'high', images: ['https://images.unsplash.com/photo-1556742049-0cfed4f6a45d?w=800&h=600&fit=crop', 'https://images.unsplash.com/photo-1551288049-bebda4e38f71?w=800&h=600&fit=crop'], githubIssue: { issueNumber: 101, issueUrl: 'https://github.com/qa-demo-org/mobile-shop-app/issues/101', state: 'closed', repoOwner: 'qa-demo-org', repoName: 'mobile-shop-app' }, createdAt: '2026-04-05T00:00:00', updatedAt: '2026-04-12T00:00:00' },
      { id: 2, updateId: 1, title: '간편결제(토스) 취소 후 재시도 불가', description: '토스페이 결제 취소 후 동일 세션에서 재결제 시 "세션 만료" 오류 발생.', category: '결제', status: 'in_progress', testerId: 4, assignee1Id: 2, assignee2Id: null, priority: 'critical', images: ['https://images.unsplash.com/photo-1563013544-824ae1b704d3?w=800&h=600&fit=crop'], githubIssue: { issueNumber: 102, issueUrl: 'https://github.com/qa-demo-org/mobile-shop-app/issues/102', state: 'open', repoOwner: 'qa-demo-org', repoName: 'mobile-shop-app' }, createdAt: '2026-04-06T00:00:00', updatedAt: '2026-04-18T00:00:00' },
      { id: 3, updateId: 1, title: '영수증 이메일 발송 지연', description: '결제 완료 후 영수증 이메일이 10분 이상 지연되어 발송됨. 평균 30초 내 발송 필요.', category: '결제', status: 'needs_fix', testerId: null, assignee1Id: 3, assignee2Id: null, priority: 'medium', images: [], githubIssue: null, createdAt: '2026-04-08T00:00:00', updatedAt: '2026-04-08T00:00:00' },
      { id: 4, updateId: 2, title: '푸시 클릭 시 앱 미실행', description: '백그라운드 상태에서 푸시 알림 클릭 시, 앱이 포그라운드로 전환되지 않음.', category: '알림', status: 'needs_fix', testerId: null, assignee1Id: 1, assignee2Id: null, priority: 'high', images: ['https://images.unsplash.com/photo-1512941937669-90a1b58e7e9c?w=800&h=600&fit=crop', 'https://images.unsplash.com/photo-1551650975-87deedd944c3?w=800&h=600&fit=crop', 'https://images.unsplash.com/photo-1516321318423-f06f85e504b3?w=800&h=600&fit=crop'], githubIssue: null, createdAt: '2026-04-22T00:00:00', updatedAt: '2026-04-22T00:00:00' },
      { id: 5, updateId: 2, title: '딥링크 파라미터 누락', description: '푸시 알림의 딥링크 URL에 campaign_id 파라미터가 누락되어 추적 불가.', category: '알림', status: 'fix_done', testerId: 4, assignee1Id: 2, assignee2Id: null, priority: 'low', images: [], githubIssue: null, createdAt: '2026-04-23T00:00:00', updatedAt: '2026-04-25T00:00:00' },
      { id: 6, updateId: 5, title: 'AI 위젯 예측값 0으로 표시', description: '트래픽 급증 상황에서 AI 이상탐지 위젯의 예측값이 0으로 고정 표시됨.', category: '홈 개편', status: 'in_progress', testerId: 4, assignee1Id: 4, assignee2Id: 2, priority: 'critical', images: ['https://images.unsplash.com/photo-1460925895917-afdab827c52f?w=800&h=600&fit=crop'], githubIssue: null, createdAt: '2026-05-12T00:00:00', updatedAt: '2026-05-14T00:00:00' },
      { id: 7, updateId: 5, title: '위젯 새로고침 시 데이터 섞임', description: '수동 새로고침 후 위젯 간 데이터가 섞여 표시됨. 캐싱 문제로 추정.', category: '홈 개편', status: 'needs_recheck', testerId: null, assignee1Id: 5, assignee2Id: null, priority: 'medium', images: [], githubIssue: null, createdAt: '2026-05-13T00:00:00', updatedAt: '2026-05-13T00:00:00' },
      { id: 8, updateId: 6, title: '카카오 친구 목록 동기화 실패', description: '친구 목록 API 호출 시 500 에러 반환. SDK 버전 충돌 가능성.', category: '카카오 연동', status: 'on_hold', testerId: null, assignee1Id: 3, assignee2Id: null, priority: 'high', images: [], githubIssue: null, createdAt: '2026-03-22T00:00:00', updatedAt: '2026-03-22T00:00:00' },
      { id: 9, updateId: 7, title: 'MFA QR 코드 스캔 실패', description: '일부 Android 기기에서 QR 코드 스캔 후 인증 코드 생성 불가.', category: '인증', status: 'confirmed', testerId: 4, assignee1Id: 4, assignee2Id: null, priority: 'high', images: [], githubIssue: null, createdAt: '2025-12-05T00:00:00', updatedAt: '2025-12-10T00:00:00' },
    ],

    comments: [
      { id: 1, qaItemId: 1, parentId: null, authorId: 2, content: '백엔드에서 400 응답은 정상 반환 중인데, 프론트에서 catch 블록이 누락된 것 같아요. 확인 부탁드립니다.', images: ['https://images.unsplash.com/photo-1614741118887-7a4ee193a5fa?w=400&h=400&fit=crop'], reactions: { '👍': [1, 5], '🤔': [3] }, createdAt: '2026-04-06T00:00:00', updatedAt: '2026-04-06T00:00:00' },
      { id: 2, qaItemId: 1, parentId: 1, authorId: 1, content: '확인했습니다. @박서연 님, PaymentErrorBoundary에 처리 로직 추가해서 PR 올렸어요. #2842', images: [], reactions: { '👍': [2] }, createdAt: '2026-04-08T00:00:00', updatedAt: '2026-04-08T00:00:00' },
      { id: 3, qaItemId: 2, parentId: null, authorId: 3, content: 'Redis 세션 TTL 설정이 취소 시 0으로 변경되는 것 확인. 세션 재생성 로직 확인이 필요합니다.', images: [], reactions: {}, createdAt: '2026-04-10T00:00:00', updatedAt: '2026-04-10T00:00:00' },
      { id: 4, qaItemId: 6, parentId: null, authorId: 5, content: 'AI 모델 버전이 v2.1로 업데이트되면서 입력 스키마가 변경된 것 같습니다. 백엔드 로그 확인 중.', images: [], reactions: {}, createdAt: '2026-05-13T00:00:00', updatedAt: '2026-05-13T00:00:00' },
      { id: 5, qaItemId: 1, parentId: 1, authorId: 2, content: 'PR 확인 완료. 머지 감사합니다!', images: [], reactions: { '🎉': [1] }, createdAt: '2026-04-09T00:00:00', updatedAt: '2026-04-09T00:00:00' },
    ],

    /* 데모는 GitHub App 이 "설정된 상태"로 시드 — 설치된 가짜 repo 2개. */
    githubRepos: [
      { installationId: 9001, owner: 'qa-demo-org', name: 'mobile-shop-app', fullName: 'qa-demo-org/mobile-shop-app', private: true, htmlUrl: 'https://github.com/qa-demo-org/mobile-shop-app' },
      { installationId: 9001, owner: 'qa-demo-org', name: 'admin-dashboard', fullName: 'qa-demo-org/admin-dashboard', private: false, htmlUrl: 'https://github.com/qa-demo-org/admin-dashboard' },
    ],

    /* QA id → 연결된 가짜 커밋 (커밋 메시지에 #이슈번호를 남긴 상황을 재현). */
    githubCommits: {
      1: [
        { sha: 'a1b2c3d4e5f60718293a4b5c6d7e8f9012345678', shortSha: 'a1b2c3d', message: 'fix: 카드 결제 오류 응답 catch 누락 수정 (#101)\n\nPaymentErrorBoundary 에 400 응답 처리 로직 추가', authorName: '김민준', authorLogin: 'kimminjun', avatarUrl: 'https://api.dicebear.com/7.x/avataaars/svg?seed=kim', htmlUrl: 'https://github.com/qa-demo-org/mobile-shop-app/commit/a1b2c3d4e5f60718293a4b5c6d7e8f9012345678', committedAt: '2026-04-08T14:22:00' },
        { sha: 'b2c3d4e5f60718293a4b5c6d7e8f901234567890', shortSha: 'b2c3d4e', message: 'test: 결제 오류 메시지 노출 회귀 테스트 추가 (#101)', authorName: '김민준', authorLogin: 'kimminjun', avatarUrl: 'https://api.dicebear.com/7.x/avataaars/svg?seed=kim', htmlUrl: 'https://github.com/qa-demo-org/mobile-shop-app/commit/b2c3d4e5f60718293a4b5c6d7e8f901234567890', committedAt: '2026-04-09T10:05:00' },
      ],
      2: [
        { sha: 'c3d4e5f60718293a4b5c6d7e8f90123456789012', shortSha: 'c3d4e5f', message: 'fix: 결제 취소 시 Redis 세션 TTL 재설정 (#102)', authorName: '박서연', authorLogin: 'parkseoyeon', avatarUrl: 'https://api.dicebear.com/7.x/avataaars/svg?seed=park', htmlUrl: 'https://github.com/qa-demo-org/mobile-shop-app/commit/c3d4e5f60718293a4b5c6d7e8f90123456789012', committedAt: '2026-04-15T16:40:00' },
      ],
    },

    seq: 100,
    currentUserId: null,
  }
}
