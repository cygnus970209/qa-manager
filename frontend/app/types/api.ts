/**
 * 백엔드 DTO와 1:1 매핑되는 타입 정의.
 * springdoc-openapi 가 Spring Boot 4와 호환되면 openapi-typescript로 자동 생성 전환 예정.
 */

/* ─────────────── 공통 ─────────────── */
export interface ApiErrorBody {
  timestamp: string
  status: number
  code: string
  message: string
  details?: unknown
}

export interface AssigneeSummary {
  id: number
  name: string
  avatarUrl: string | null
}

/* ─────────────── Auth ─────────────── */
export interface LoginRequest {
  username: string
  password: string
}

/** 토큰은 HttpOnly 쿠키로 발행. body에는 user와 access TTL만 담김. */
export interface LoginResponse {
  expiresInSeconds: number
  user: Me
}

/**
 * 로그인/OTP 검증 통합 응답. (null 필드는 직렬화에서 제외됨)
 * - 인증 완료: authenticated=true, expiresInSeconds, user
 * - OTP 필요: authenticated=false, otpRequired=true, challengeId, maskedEmail, otpExpiresInSeconds
 */
export interface AuthResponse {
  authenticated: boolean
  otpRequired?: boolean
  expiresInSeconds?: number
  user?: Me
  challengeId?: string
  maskedEmail?: string
  otpExpiresInSeconds?: number
}

export interface OtpVerifyRequest {
  challengeId: string
  code: string
}

export interface OtpResendRequest {
  challengeId: string
}

export interface Me {
  id: number
  username: string
  name: string
  role: string | null
  avatarUrl: string | null
}

export interface UpdateMeRequest {
  name?: string
  avatarUrl?: string
}

export interface ChangeMyPasswordRequest {
  currentPassword: string
  newPassword: string
}

/* ─────────────── Member ─────────────── */
export interface Member {
  id: number
  username: string
  name: string
  email?: string | null
  role: string | null
  avatarUrl: string | null
  teamsLinked?: boolean
  teamsNotifyEnabled?: boolean
}

export interface TeamsTestResult {
  success: boolean
  errorMessage?: string | null
  memberName?: string | null
  email?: string | null
  configOk: boolean
  notifyEnabled: boolean
  aadMapped: boolean
  teamsUserId?: string | null
  chatOk: boolean
  chatId?: string | null
  sent: boolean
}

/** 본인 알림 개인화 설정 (Teams 발송에만 적용). GET/PUT /api/me/notification-settings */
export interface NotificationSettings {
  teamsNotifyEnabled: boolean
  notifyQaEnabled: boolean
  notifyCommentEnabled: boolean
  notifyReplyEnabled: boolean
  quietHoursStart: string | null
  quietHoursEnd: string | null
}

export interface MemberCreateRequest {
  username: string
  password: string
  name: string
  role?: string
  avatarUrl?: string
}

export interface MemberUpdateRequest {
  name?: string
  role?: string
  avatarUrl?: string
}

/* ─────────────── Project ─────────────── */
export type ProjectStatus = 'active' | 'completed' | 'paused'

export interface Project {
  id: number
  name: string
  description: string | null
  status: ProjectStatus
  pinned: boolean
  createdAt: string | null
  updatedAt: string | null
  /** GitHub repo 연결 정보. non_null 직렬화라 미연결 시 필드 자체가 생략됨. */
  githubInstallationId?: number | null
  githubRepoOwner?: string | null
  githubRepoName?: string | null
}

export interface ProjectCreateRequest {
  name: string
  description?: string
  status: 'ACTIVE' | 'COMPLETED' | 'PAUSED'
}

export interface ProjectUpdateRequest {
  name?: string
  description?: string
  status?: 'ACTIVE' | 'COMPLETED' | 'PAUSED'
  /** GitHub repo 연결 — 셋 다 함께 보내야 함. */
  githubInstallationId?: number
  githubRepoOwner?: string
  githubRepoName?: string
  /** GitHub repo 연결 해제. */
  clearGithubRepo?: boolean
}

/* ─────────────── ProjectUpdate (버전) ─────────────── */
export type UpdateStatus = 'in_progress' | 'testing' | 'released'

export interface ProjectUpdate {
  id: number
  projectId: number
  version: string
  title: string
  description: string | null
  status: UpdateStatus
  createdAt: string | null
  updatedAt: string | null
}

export interface UpdateCreateRequest {
  version: string
  title: string
  description?: string
  status: 'IN_PROGRESS' | 'TESTING' | 'RELEASED'
}

export type UpdatePatchRequest = Partial<UpdateCreateRequest>

/* ─────────────── QA Item ─────────────── */
export type QaStatus =
  | 'needs_fix'      // 수정필요
  | 'in_progress'    // 진행중
  | 'fix_done'       // 수정완료
  | 'confirmed'      // 확인완료
  | 'on_hold'        // 보류
  | 'needs_recheck'  // 추가확인필요

export type QaStatusUpper =
  | 'NEEDS_FIX'
  | 'IN_PROGRESS'
  | 'FIX_DONE'
  | 'CONFIRMED'
  | 'ON_HOLD'
  | 'NEEDS_RECHECK'
export type QaPriority = 'low' | 'medium' | 'high' | 'critical'

export interface QaItem {
  id: number
  updateId: number
  title: string
  description: string | null
  category: string | null
  status: QaStatus
  tester: AssigneeSummary | null
  assignee1: AssigneeSummary | null
  assignee2: AssigneeSummary | null
  priority: QaPriority
  images: string[]
  createdAt: string | null
  updatedAt: string | null
  /** 연결된 GitHub 이슈. non_null 직렬화라 미연결 시 필드 자체가 생략됨. */
  githubIssue?: GithubIssueInfo | null
}

/** GET /api/qa/page 응답 */
export interface QaPage {
  content: QaItem[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

/** 프로젝트별 QA 개수/해결(수정완료+확인완료) 개수 */
export interface QaProjectSummary {
  projectId: number
  count: number
  resolved: number
}

/** GET /api/qa/dashboard-stats 응답. mine=true 조회 시 모든 수치가 '내 작업' 기준 */
export interface QaDashboardStats {
  total: number
  needsFix: number
  inProgress: number
  fixDone: number
  confirmed: number
  onHold: number
  needsRecheck: number
  critical: number
  byProject: QaProjectSummary[]
}

export interface QaCreateRequest {
  updateId: number
  title: string
  description?: string
  category?: string
  status: QaStatusUpper
  /** null/생략하면 현재 로그인 사용자가 tester 로 자동 지정됨. */
  testerId?: number
  assignee1Id?: number
  assignee2Id?: number
  priority: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
  images?: string[]
  /** true 면 프로젝트에 연결된 repo 에 GitHub 이슈를 함께 생성. */
  createGithubIssue?: boolean
}

export interface QaPatchRequest {
  updateId?: number
  title?: string
  description?: string
  category?: string
  status?: QaCreateRequest['status']
  testerId?: number
  assignee1Id?: number
  assignee2Id?: number
  priority?: QaCreateRequest['priority']
  images?: string[]
  clearTester?: boolean
  clearAssignee1?: boolean
  clearAssignee2?: boolean
}

export interface QaHistoryEntry {
  id: number
  field: string
  oldValue: string | null
  newValue: string | null
  changedBy: AssigneeSummary | null
  changedAt: string | null
}

/* ─────────────── QA Comment ─────────────── */
export interface QaComment {
  id: number
  qaItemId: number
  parentId?: number | null
  author: AssigneeSummary
  content: string
  images: string[]
  reactions: Record<string, number[]>
  createdAt: string | null
  updatedAt: string | null
}

export interface CommentCreateRequest {
  parentId?: number
  content: string
  images?: string[]
  /** @멘션된 멤버 id (자동완성에서 pick 한 항목만 누적). 알림 발송 대상. */
  mentionedMemberIds?: number[]
}

export interface CommentUpdateRequest {
  content: string
  images?: string[]
}

export interface ReactionRequest {
  emoji: string
}

/* ─────────────── Notification ─────────────── */
export type NotificationType = 'qa' | 'update' | 'project' | 'mention' | 'comment' | 'reply'

export interface Notification {
  id: number
  type: NotificationType
  message: string
  projectId: number | null
  projectName: string | null
  qaItemId: number | null
  actorId: number | null
  actorName: string | null
  read: boolean
  createdAt: string | null
}

/* ─────────────── GitHub ─────────────── */
/** GET /api/github/app — GitHub App 연동 상태. */
export interface GithubAppStatus {
  configured: boolean
  appSlug: string | null
  appName: string | null
  /** 앱 설치/repo 추가 페이지. configured=false 면 null. */
  installUrl: string | null
}

/** POST /api/github/app/manifest — hidden form POST 로 GitHub 에 제출해야 함. */
export interface GithubManifestResponse {
  targetUrl: string
  manifest: string
}

export interface GithubRepo {
  installationId: number
  owner: string
  name: string
  fullName: string
  private: boolean
  htmlUrl: string
}

export interface GithubIssueInfo {
  issueNumber: number
  issueUrl: string
  state: 'open' | 'closed'
  repoOwner: string
  repoName: string
}

export interface GithubCommit {
  sha: string
  shortSha: string
  message: string
  authorName: string | null
  authorLogin: string | null
  avatarUrl: string | null
  htmlUrl: string
  committedAt: string | null
}

/* ─────────────── File / S3 ─────────────── */
export interface PresignRequest {
  fileName: string
  contentType: string
  purpose: 'qa_image' | 'comment_image' | 'avatar'
  fileSize: number
}

export interface PresignResponse {
  key: string
  uploadUrl: string
  publicUrl: string
  expiresInSeconds: number
}
