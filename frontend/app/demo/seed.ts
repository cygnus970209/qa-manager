import type { DemoState } from './types'

export type SeedLocale = 'ko' | 'en'

/**
 * 시드 언어 결정: i18n 쿠키(qam_locale) → 브라우저 언어 → ko.
 * (데모 db 는 클라이언트에서만 시드되므로 document/navigator 접근 가능)
 */
function resolveSeedLocale(): SeedLocale {
  try {
    const cookie = document.cookie.match(/(?:^|;\s*)qam_locale=([^;]+)/)?.[1]
    const lang = cookie ?? navigator.language ?? 'ko'
    return lang.toLowerCase().startsWith('ko') ? 'ko' : 'en'
  } catch {
    return 'ko'
  }
}

/**
 * 데모 초기 상태. 백엔드 시드(V2/V3)를 현재 프론트 타입(6-state, tester/assignee1/2)에 맞춰 재작성.
 * 매 호출마다 새 객체를 만들어 반환하므로 reset 시 안전하게 재사용된다.
 * 비밀번호는 데모 안내용 평문(전부 1234) — 실제 인증이 아니므로 해시하지 않는다.
 * 구조(ID·날짜·상태)는 한국어 원본 한 벌만 유지하고, 영어는 텍스트 필드만 id 기준으로 덮어쓴다.
 */
export function createSeed(locale?: SeedLocale): DemoState {
  const state = createKoSeed()
  if ((locale ?? resolveSeedLocale()) === 'en') applyEnglishTexts(state)
  fillNotificationTitles(state)
  return state
}

/** 알림 제목 = 연결된 QA 제목 (백엔드가 title 에 QA 제목 스냅샷을 넣는 것과 동일). 언어 적용 후 호출한다. */
function fillNotificationTitles(s: DemoState): void {
  for (const n of s.notifications) {
    if (n.title != null || n.qaItemId == null) continue
    n.title = s.qa.find((q) => q.id === n.qaItemId)?.title ?? null
  }
}

function createKoSeed(): DemoState {
  return {
    members: [
      { id: 1, username: 'kimminjun', password: '1234', accountRole: 'ADMIN', name: '김민준', role: 'FE 개발자', email: null, avatarUrl: 'https://api.dicebear.com/7.x/avataaars/svg?seed=kim' },
      { id: 2, username: 'parkseoyeon', password: '1234', accountRole: 'MEMBER', name: '박서연', role: 'BE 개발자', email: null, avatarUrl: 'https://api.dicebear.com/7.x/avataaars/svg?seed=park' },
      { id: 3, username: 'leedoyoon', password: '1234', accountRole: 'MEMBER', name: '이도윤', role: 'DevOps', email: null, avatarUrl: 'https://api.dicebear.com/7.x/avataaars/svg?seed=lee' },
      { id: 4, username: 'choijiu', password: '1234', accountRole: 'MEMBER', name: '최지우', role: 'QA 엔지니어', email: null, avatarUrl: 'https://api.dicebear.com/7.x/avataaars/svg?seed=choi' },
      { id: 5, username: 'jeonghyunwoo', password: '1234', accountRole: 'MEMBER', name: '정현우', role: '풀스택', email: null, avatarUrl: 'https://api.dicebear.com/7.x/avataaars/svg?seed=jeong' },
      // OTP 화면 체험용 계정 — 로그인 시 이메일 OTP 2단계로 진입 (데모 인증 코드 123456).
      { id: 6, username: 'hanboan', password: '1234', accountRole: 'ADMIN', name: '한보안', role: '보안 담당자', email: 'security@qamanager.dev', avatarUrl: 'https://api.dicebear.com/7.x/avataaars/svg?seed=han', otpEnabled: true },
    ],

    projects: [
      { id: 1, name: '모바일 쇼핑몰 앱', description: 'iOS/Android 하이브리드 쇼핑몰 앱 리뉴얼 프로젝트. 신규 결제 시스템 도입 및 UI/UX 개선.', status: 'active', pinnedBy: [], githubRepos: [{ installationId: 9001, repoOwner: 'qa-demo-org', repoName: 'mobile-shop-app' }], createdAt: '2026-01-15T00:00:00', updatedAt: '2026-01-15T00:00:00' },
      { id: 2, name: '관리자 대시보드', description: '내부 운영팀을 위한 실시간 데이터 분석 및 모니터링 대시보드 구축.', status: 'active', pinnedBy: [], githubRepos: [{ installationId: 9001, repoOwner: 'qa-demo-org', repoName: 'admin-dashboard' }], createdAt: '2026-02-20T00:00:00', updatedAt: '2026-02-20T00:00:00' },
      { id: 3, name: 'SNS 연동 API', description: '카카오톡, 인스타그램, 네이버 등 외부 SNS 플랫폼 연동 API 개발.', status: 'paused', pinnedBy: [], githubRepos: [], createdAt: '2026-03-10T00:00:00', updatedAt: '2026-03-10T00:00:00' },
      { id: 4, name: '회원 시스템 v2', description: '기존 회원 인증 시스템 전면 개편. OAuth2 + MFA 다중 인증 도입.', status: 'completed', pinnedBy: [], githubRepos: [], createdAt: '2025-11-01T00:00:00', updatedAt: '2025-11-01T00:00:00' },
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

    /* 알림센터 시드 — 어느 데모 계정으로 로그인해도 알림이 보이도록 멤버별로 구성.
     * 메시지 문구는 백엔드 NotificationService 형식과 동일하게 맞춘다 (title = QA 제목, message = 본문). */
    notifications: [
      // 김민준(1) — FE 개발자
      { id: 81, recipientId: 1, type: 'mention', title: null, message: '코멘트에서 언급되었습니다: @김민준 님, 프론트 catch 블록 누락 확인 부탁드립니다.', projectId: 1, qaItemId: 1, actorId: 2, read: false, createdAt: '2026-05-14T10:20:00' },
      { id: 82, recipientId: 1, type: 'qa', title: null, message: 'QA가 배정되었습니다', projectId: 1, qaItemId: 4, actorId: 4, read: false, createdAt: '2026-04-22T09:12:00' },
      { id: 83, recipientId: 1, type: 'reply', title: null, message: '내 코멘트에 답글이 달렸습니다: PR 확인 완료. 머지 감사합니다!', projectId: 1, qaItemId: 1, actorId: 2, read: true, createdAt: '2026-04-09T11:00:00' },
      // 박서연(2) — BE 개발자
      { id: 84, recipientId: 2, type: 'qa', title: null, message: 'QA가 배정되었습니다', projectId: 1, qaItemId: 2, actorId: 4, read: false, createdAt: '2026-05-13T15:40:00' },
      { id: 85, recipientId: 2, type: 'mention', title: null, message: '코멘트에서 언급되었습니다: 확인했습니다. @박서연 님, PaymentErrorBoundary에 처리 로직 추가해서 PR 올렸어요. #2842', projectId: 1, qaItemId: 1, actorId: 1, read: false, createdAt: '2026-04-08T13:05:00' },
      { id: 86, recipientId: 2, type: 'comment', title: null, message: '새 코멘트가 달렸습니다: AI 모델 버전이 v2.1로 업데이트되면서 입력 스키마가 변경된 것 같습니다. 백엔드 로그 확인 중.', projectId: 2, qaItemId: 6, actorId: 5, read: true, createdAt: '2026-05-13T09:30:00' },
      // 이도윤(3) — DevOps
      { id: 87, recipientId: 3, type: 'qa', title: null, message: 'QA가 배정되었습니다', projectId: 1, qaItemId: 3, actorId: 4, read: false, createdAt: '2026-04-08T10:00:00' },
      { id: 88, recipientId: 3, type: 'qa', title: null, message: 'QA 상태 변경 → 보류', projectId: 3, qaItemId: 8, actorId: 4, read: true, createdAt: '2026-03-25T17:20:00' },
      // 최지우(4) — QA 엔지니어
      { id: 89, recipientId: 4, type: 'comment', title: null, message: '새 코멘트가 달렸습니다: Redis 세션 TTL 설정이 취소 시 0으로 변경되는 것 확인. 세션 재생성 로직 확인이 필요합니다.', projectId: 1, qaItemId: 2, actorId: 3, read: false, createdAt: '2026-05-14T14:10:00' },
      { id: 90, recipientId: 4, type: 'qa', title: null, message: 'QA 상태 변경 → 수정완료', projectId: 1, qaItemId: 5, actorId: 2, read: false, createdAt: '2026-04-25T18:00:00' },
      { id: 91, recipientId: 4, type: 'qa', title: null, message: '새 QA가 등록되었습니다', projectId: 2, qaItemId: 7, actorId: 5, read: true, createdAt: '2026-05-13T08:45:00' },
      // 정현우(5) — 풀스택
      { id: 92, recipientId: 5, type: 'qa', title: null, message: 'QA가 배정되었습니다', projectId: 2, qaItemId: 7, actorId: 4, read: false, createdAt: '2026-05-13T09:00:00' },
      { id: 93, recipientId: 5, type: 'comment', title: null, message: '새 코멘트가 달렸습니다: 입력 스키마 변경 확인했습니다. 어댑터 수정 후 재배포하겠습니다.', projectId: 2, qaItemId: 6, actorId: 2, read: true, createdAt: '2026-05-14T11:25:00' },
    ],

    /* 테스트 케이스 관리 시드 — 모바일 쇼핑몰 앱(프로젝트 1) 기준. */
    testSuites: [
      { id: 1, projectId: 1, name: '결제', sortOrder: 0 },
      { id: 2, projectId: 1, name: '공통', sortOrder: 1 },
    ],

    testCases: [
      { id: 1, projectId: 1, suiteId: 1, title: '정상 카드 결제', precondition: '테스트 카드가 등록된 계정으로 로그인한 상태', steps: [
        { action: '상품을 장바구니에 담고 결제 화면으로 이동한다', expected: '주문 금액과 결제 수단이 표시된다' },
        { action: '등록된 카드로 결제 버튼을 누른다', expected: '결제가 승인되고 완료 화면이 표시된다' },
        { action: '주문 내역을 확인한다', expected: '주문이 결제 완료 상태로 표시된다' },
      ], priority: 'critical', origin: 'FLOW', flowId: 1, flowStale: false, createdAt: '2026-04-02T00:00:00', updatedAt: '2026-04-02T00:00:00' },
      { id: 2, projectId: 1, suiteId: 1, title: '잔액 부족 카드 결제 거절', precondition: '잔액이 부족한 테스트 카드 보유', steps: [
        { action: '잔액 부족 카드로 결제를 시도한다', expected: '결제가 거절된다' },
        { action: '안내 문구를 확인한다', expected: '"잔액이 부족합니다" 문구가 한국어로 표시된다' },
      ], priority: 'high', origin: 'MANUAL', flowId: null, flowStale: false, createdAt: '2026-04-02T00:00:00', updatedAt: '2026-04-02T00:00:00' },
      { id: 3, projectId: 1, suiteId: 1, title: '간편결제 토스 결제', precondition: '토스 앱이 설치된 기기', steps: [
        { action: '결제 수단에서 토스페이를 선택한다', expected: '토스 앱 인증 화면으로 이동한다' },
        { action: '토스 앱에서 인증을 완료한다', expected: '앱으로 복귀하고 결제 완료 화면이 표시된다' },
      ], priority: 'medium', origin: 'MANUAL', flowId: null, flowStale: false, createdAt: '2026-04-03T00:00:00', updatedAt: '2026-04-03T00:00:00' },
      { id: 4, projectId: 1, suiteId: 2, title: '로그인/로그아웃', precondition: null, steps: [
        { action: '데모 계정으로 로그인한다', expected: '홈 화면으로 이동한다' },
        { action: '설정에서 로그아웃한다', expected: '로그인 화면으로 돌아간다' },
      ], priority: 'medium', origin: 'MANUAL', flowId: null, flowStale: false, createdAt: '2026-04-03T00:00:00', updatedAt: '2026-04-03T00:00:00' },
      { id: 5, projectId: 1, suiteId: 2, title: '다국어 전환', precondition: null, steps: [
        { action: '설정에서 언어를 English 로 변경한다', expected: '모든 화면 텍스트가 영어로 표시된다' },
        { action: '다시 한국어로 변경한다', expected: '텍스트가 한국어로 돌아온다' },
      ], priority: 'low', origin: 'MANUAL', flowId: null, flowStale: false, createdAt: '2026-04-04T00:00:00', updatedAt: '2026-04-04T00:00:00' },
    ],

    testFlows: [
      { id: 1, projectId: 1, updateId: 1, name: '결제 워크플로우', graph: {
        nodes: [
          { id: 'n1', type: 'start', label: '시작', position: { x: 40, y: 200 } },
          { id: 'n2', type: 'screen', label: '결제 화면', image: 'https://images.unsplash.com/photo-1556742049-0cfed4f6a45d?w=800&h=600&fit=crop', expected: '주문 금액과 결제 수단이 표시된다', position: { x: 200, y: 200 } },
          { id: 'n3', type: 'action', label: '결제 버튼 클릭', position: { x: 380, y: 200 } },
          { id: 'n4', type: 'decision', label: 'OTP 인증 필요?', position: { x: 560, y: 200 } },
          { id: 'n5', type: 'action', label: 'OTP 입력', expected: '인증 성공 시 결제가 진행된다', position: { x: 720, y: 120 } },
          { id: 'n6', type: 'screen', label: '결제 완료', expected: '결제 완료 화면과 영수증 안내가 표시된다', position: { x: 880, y: 200 } },
          { id: 'n7', type: 'end', label: '종료', position: { x: 1040, y: 200 } },
        ],
        edges: [
          { id: 'e1', source: 'n1', target: 'n2' },
          { id: 'e2', source: 'n2', target: 'n3' },
          { id: 'e3', source: 'n3', target: 'n4' },
          { id: 'e4', source: 'n4', target: 'n5', label: '예' },
          { id: 'e5', source: 'n4', target: 'n6', label: '아니오' },
          { id: 'e6', source: 'n5', target: 'n6' },
          { id: 'e7', source: 'n6', target: 'n7' },
        ],
      }, updatedAt: '2026-04-05T00:00:00' },
    ],

    testRuns: [
      { id: 1, updateId: 1, name: 'v2.3.0 결제 회귀', closedAt: null, createdAt: '2026-04-10T00:00:00' },
    ],

    /* 런 케이스는 생성 시점의 케이스 스냅샷(caseId 는 원본 참조용). */
    testRunCases: [
      { id: 1, runId: 1, caseId: 1, platform: 'PC', sortOrder: 0, title: '정상 카드 결제', precondition: '테스트 카드가 등록된 계정으로 로그인한 상태', steps: [
        { action: '상품을 장바구니에 담고 결제 화면으로 이동한다', expected: '주문 금액과 결제 수단이 표시된다' },
        { action: '등록된 카드로 결제 버튼을 누른다', expected: '결제가 승인되고 완료 화면이 표시된다' },
        { action: '주문 내역을 확인한다', expected: '주문이 결제 완료 상태로 표시된다' },
      ], priority: 'critical', result: 'PASS', note: null, qaItemId: null, executedAt: '2026-04-11T10:20:00' },
      { id: 2, runId: 1, caseId: 2, platform: 'ANDROID', sortOrder: 1, title: '잔액 부족 카드 결제 거절', precondition: '잔액이 부족한 테스트 카드 보유', steps: [
        { action: '잔액 부족 카드로 결제를 시도한다', expected: '결제가 거절된다' },
        { action: '안내 문구를 확인한다', expected: '"잔액이 부족합니다" 문구가 한국어로 표시된다' },
      ], priority: 'high', result: 'FAIL', note: '잔액 부족 문구가 영어로 나옴', qaItemId: 2, executedAt: '2026-04-11T10:35:00' },
      { id: 3, runId: 1, caseId: 3, platform: 'IOS', sortOrder: 2, title: '간편결제 토스 결제', precondition: '토스 앱이 설치된 기기', steps: [
        { action: '결제 수단에서 토스페이를 선택한다', expected: '토스 앱 인증 화면으로 이동한다' },
        { action: '토스 앱에서 인증을 완료한다', expected: '앱으로 복귀하고 결제 완료 화면이 표시된다' },
      ], priority: 'medium', result: 'PENDING', note: null, qaItemId: null, executedAt: null },
    ],

    seq: 100,
    currentUserId: null,
  }
}

/* ─────────────── 영어 데모 텍스트 오버레이 ───────────────
 * 구조는 위 한국어 시드가 원본. 여기는 사용자에게 보이는 텍스트 필드만 id 기준으로 덮어쓴다.
 * 새 엔티티를 시드에 추가하면 이 오버레이에도 같은 id 로 영어 텍스트를 추가할 것. */

const EN_TEXTS = {
  members: {
    1: { name: 'Minjun Kim', role: 'Frontend Engineer' },
    2: { name: 'Seoyeon Park', role: 'Backend Engineer' },
    3: { name: 'Doyoon Lee', role: 'DevOps' },
    4: { name: 'Jiwoo Choi', role: 'QA Engineer' },
    5: { name: 'Hyunwoo Jung', role: 'Full-stack' },
    6: { name: 'Boan Han', role: 'Security Lead' },
  } as Record<number, { name: string; role: string }>,

  projects: {
    1: { name: 'Mobile Shopping App', description: 'iOS/Android hybrid shopping app renewal — new payment system and UI/UX improvements.' },
    2: { name: 'Admin Dashboard', description: 'Real-time analytics and monitoring dashboard for the internal operations team.' },
    3: { name: 'Social Media API', description: 'Integration APIs for external social platforms such as KakaoTalk, Instagram, and Naver.' },
    4: { name: 'Membership System v2', description: 'Complete overhaul of the legacy member auth system. OAuth2 + MFA rollout.' },
  } as Record<number, { name: string; description: string }>,

  updates: {
    1: { title: 'Payment Module Revamp', description: 'Improved PG integration and new express-checkout options.' },
    2: { title: 'Push Notification Upgrade', description: 'A/B-test-driven push personalization and deep link handling.' },
    3: { title: 'Cart Bug Fixes', description: 'Fixed cart total not updating when item quantity changes.' },
    4: { title: 'Initial Dashboard Release', description: 'Core KPI widgets and real-time data streaming.' },
    5: { title: 'AI Anomaly Detection Widget', description: 'Automated anomaly alert widget.' },
    6: { title: 'KakaoTalk Integration Beta', description: 'Beta test of the KakaoTalk message API integration.' },
    7: { title: 'MFA Rollout', description: 'Multi-factor authentication with Google Authenticator.' },
  } as Record<number, { title: string; description: string }>,

  qa: {
    1: { title: 'No error message on card payment failure', description: 'When an invalid card number is entered, no error message is shown — only an endless loading spinner.', category: 'Payments' },
    2: { title: 'Express checkout (Toss) retry fails after cancel', description: '"Session expired" error when retrying a payment in the same session after canceling a Toss Pay payment.', category: 'Payments' },
    3: { title: 'Receipt emails delayed', description: 'Receipt emails arrive 10+ minutes after payment; they should go out within 30 seconds.', category: 'Payments' },
    4: { title: 'App not opening from push tap', description: 'Tapping a push notification while the app is in the background does not bring it to the foreground.', category: 'Notifications' },
    5: { title: 'Missing deep link parameter', description: 'Push deep link URLs are missing the campaign_id parameter, breaking attribution.', category: 'Notifications' },
    6: { title: 'AI widget prediction stuck at 0', description: 'During traffic spikes the anomaly-detection widget shows a constant 0 prediction.', category: 'Home Revamp' },
    7: { title: 'Widget data mixed up after refresh', description: 'After a manual refresh, data bleeds between widgets. Likely a caching issue.', category: 'Home Revamp' },
    8: { title: 'Kakao friend list sync fails', description: 'Friend list API returns a 500 error. Possible SDK version conflict.', category: 'Kakao Integration' },
    9: { title: 'MFA QR code scan fails', description: 'Some Android devices cannot generate auth codes after scanning the QR code.', category: 'Auth' },
  } as Record<number, { title: string; description: string; category: string }>,

  comments: {
    1: { content: 'The backend returns the 400 correctly — looks like the frontend is missing a catch block. Could you take a look?' },
    2: { content: 'Confirmed. @Seoyeon Park — added handling to PaymentErrorBoundary and opened a PR. #2842' },
    3: { content: 'Verified that the Redis session TTL gets set to 0 on cancel. The session re-creation logic needs review.' },
    4: { content: 'Looks like the input schema changed when the AI model moved to v2.1. Checking backend logs.' },
    5: { content: 'Reviewed the PR — thanks for the merge!' },
  } as Record<number, { content: string }>,

  /* shortSha → 커밋 메시지/작성자 */
  commits: {
    a1b2c3d: { message: 'fix: handle missing catch on card payment error response (#101)\n\nAdded 400 handling to PaymentErrorBoundary', authorName: 'Minjun Kim' },
    b2c3d4e: { message: 'test: add regression test for payment error message (#101)', authorName: 'Minjun Kim' },
    c3d4e5f: { message: 'fix: reset Redis session TTL on payment cancel (#102)', authorName: 'Seoyeon Park' },
  } as Record<string, { message: string; authorName: string }>,

  testSuites: {
    1: { name: 'Payments' },
    2: { name: 'Common' },
  } as Record<number, { name: string }>,

  testCases: {
    1: { title: 'Successful card payment', precondition: 'Signed in with an account that has a registered test card', steps: [
      { action: 'Add an item to the cart and go to checkout', expected: 'The order total and payment methods are shown' },
      { action: 'Pay with the registered card', expected: 'The payment is approved and the completion screen is shown' },
      { action: 'Check the order history', expected: 'The order is shown as paid' },
    ] },
    2: { title: 'Card declined on insufficient balance', precondition: 'A test card with insufficient balance', steps: [
      { action: 'Attempt a payment with the low-balance card', expected: 'The payment is declined' },
      { action: 'Check the notice message', expected: 'An "Insufficient balance" message is shown in the app language' },
    ] },
    3: { title: 'Express checkout with Toss Pay', precondition: 'A device with the Toss app installed', steps: [
      { action: 'Select Toss Pay as the payment method', expected: 'The Toss app auth screen opens' },
      { action: 'Complete authentication in the Toss app', expected: 'The app returns and shows the completion screen' },
    ] },
    4: { title: 'Sign in / sign out', steps: [
      { action: 'Sign in with a demo account', expected: 'The home screen is shown' },
      { action: 'Sign out from settings', expected: 'The sign-in screen is shown again' },
    ] },
    5: { title: 'Language switching', steps: [
      { action: 'Change the language to English in settings', expected: 'All screen text is shown in English' },
      { action: 'Switch back to Korean', expected: 'The text returns to Korean' },
    ] },
  } as Record<number, { title: string; precondition?: string; steps: { action: string; expected: string }[] }>,

  /* 플로우: 이름 + 노드/엣지 id 기준 라벨·expected 오버레이 */
  testFlows: {
    1: {
      name: 'Payment Workflow',
      nodes: {
        n1: { label: 'Start' },
        n2: { label: 'Checkout Screen', expected: 'The order total and payment methods are shown' },
        n3: { label: 'Tap Pay Button' },
        n4: { label: 'OTP Required?' },
        n5: { label: 'Enter OTP', expected: 'The payment proceeds after successful auth' },
        n6: { label: 'Payment Complete', expected: 'The completion screen and receipt notice are shown' },
        n7: { label: 'End' },
      } as Record<string, { label: string; expected?: string }>,
      edges: { e4: { label: 'Yes' }, e5: { label: 'No' } } as Record<string, { label: string }>,
    },
  } as Record<number, { name: string; nodes: Record<string, { label: string; expected?: string }>; edges: Record<string, { label: string }> }>,

  testRuns: {
    1: { name: 'v2.3.0 Payment Regression' },
  } as Record<number, { name: string }>,

  testRunCases: {
    1: { title: 'Successful card payment', precondition: 'Signed in with an account that has a registered test card', steps: [
      { action: 'Add an item to the cart and go to checkout', expected: 'The order total and payment methods are shown' },
      { action: 'Pay with the registered card', expected: 'The payment is approved and the completion screen is shown' },
      { action: 'Check the order history', expected: 'The order is shown as paid' },
    ] },
    2: { title: 'Card declined on insufficient balance', precondition: 'A test card with insufficient balance', steps: [
      { action: 'Attempt a payment with the low-balance card', expected: 'The payment is declined' },
      { action: 'Check the notice message', expected: 'An "Insufficient balance" message is shown in the app language' },
    ], note: 'Insufficient-balance message shows in English' },
    3: { title: 'Express checkout with Toss Pay', precondition: 'A device with the Toss app installed', steps: [
      { action: 'Select Toss Pay as the payment method', expected: 'The Toss app auth screen opens' },
      { action: 'Complete authentication in the Toss app', expected: 'The app returns and shows the completion screen' },
    ] },
  } as Record<number, { title: string; precondition?: string; steps: { action: string; expected: string }[]; note?: string }>,

  notifications: {
    81: 'You were mentioned in a comment: @Minjun Kim — could you check the missing catch block on the frontend?',
    82: 'A QA item was assigned to you',
    83: 'New reply to your comment: Reviewed the PR — thanks for the merge!',
    84: 'A QA item was assigned to you',
    85: 'You were mentioned in a comment: Confirmed. @Seoyeon Park — added handling to PaymentErrorBoundary and opened a PR. #2842',
    86: 'New comment: Looks like the input schema changed when the AI model moved to v2.1. Checking backend logs.',
    87: 'A QA item was assigned to you',
    88: 'QA status changed → On Hold',
    89: 'New comment: Verified that the Redis session TTL gets set to 0 on cancel. The session re-creation logic needs review.',
    90: 'QA status changed → Fixed',
    91: 'New QA item created',
    92: 'A QA item was assigned to you',
    93: 'New comment: Confirmed the schema change. Will patch the adapter and redeploy.',
  } as Record<number, string>,
}

function applyEnglishTexts(s: DemoState): void {
  for (const m of s.members) Object.assign(m, EN_TEXTS.members[m.id])
  for (const p of s.projects) Object.assign(p, EN_TEXTS.projects[p.id])
  for (const u of s.updates) Object.assign(u, EN_TEXTS.updates[u.id])
  for (const q of s.qa) Object.assign(q, EN_TEXTS.qa[q.id])
  for (const c of s.comments) Object.assign(c, EN_TEXTS.comments[c.id])
  for (const commits of Object.values(s.githubCommits)) {
    for (const c of commits) {
      const t = EN_TEXTS.commits[c.shortSha]
      if (t) Object.assign(c, t)
    }
  }
  for (const n of s.notifications) {
    const msg = EN_TEXTS.notifications[n.id]
    if (msg) n.message = msg
  }
  for (const su of s.testSuites) Object.assign(su, EN_TEXTS.testSuites[su.id])
  for (const tc of s.testCases) Object.assign(tc, EN_TEXTS.testCases[tc.id])
  for (const f of s.testFlows) {
    const t = EN_TEXTS.testFlows[f.id]
    if (!t) continue
    f.name = t.name
    for (const n of f.graph.nodes) Object.assign(n, t.nodes[n.id])
    for (const e of f.graph.edges) {
      const et = t.edges[e.id]
      if (et) e.label = et.label
    }
  }
  for (const r of s.testRuns) Object.assign(r, EN_TEXTS.testRuns[r.id])
  for (const rc of s.testRunCases) Object.assign(rc, EN_TEXTS.testRunCases[rc.id])
}
