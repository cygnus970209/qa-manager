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
}

export interface QaPatchRequest {
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

/* ─────────────── File / S3 ─────────────── */
export interface PresignRequest {
  fileName: string
  contentType: string
  purpose: 'qa_image' | 'comment_image' | 'avatar'
}

export interface PresignResponse {
  key: string
  uploadUrl: string
  publicUrl: string
  expiresInSeconds: number
}
