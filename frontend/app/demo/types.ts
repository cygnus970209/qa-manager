/**
 * 데모 mock 백엔드의 내부 엔티티 모델.
 * 관계는 모두 id 참조로 보관하고, API 응답 시 db.ts 의 *Dto 변환기가
 * ~/types/api 의 DTO 형태(AssigneeSummary 조립 등)로 바꾼다.
 */
import type { GithubCommit, GithubIssueInfo, GithubRepo, NotificationType, QaPriority, QaStatus, ProjectStatus, UpdateStatus } from '~/types/api'

export interface DemoMember {
  id: number
  username: string
  password: string
  name: string
  role: string | null
  email: string | null
  avatarUrl: string | null
  /** true 면 로그인 시 이메일 OTP 2단계를 요구한다 (데모 체험용, 코드 123456 고정). */
  otpEnabled?: boolean
}

export interface DemoProject {
  id: number
  name: string
  description: string | null
  status: ProjectStatus
  /** 핀은 사용자별이므로 핀을 건 멤버 id 목록으로 보관. DTO 의 pinned 는 현재 사용자 기준으로 계산. */
  pinnedBy: number[]
  /** 연결된 GitHub repo 목록. */
  githubRepos: { installationId: number; repoOwner: string; repoName: string }[]
  createdAt: string
  updatedAt: string
}

export interface DemoUpdate {
  id: number
  projectId: number
  version: string
  title: string
  description: string | null
  status: UpdateStatus
  sortOrder: number
  createdAt: string
  updatedAt: string
}

export interface DemoQa {
  id: number
  updateId: number
  title: string
  description: string | null
  category: string | null
  status: QaStatus
  testerId: number | null
  assignee1Id: number | null
  assignee2Id: number | null
  priority: QaPriority
  images: string[]
  /** 연결된 GitHub 이슈 (미연결 시 null). */
  githubIssue: GithubIssueInfo | null
  createdAt: string
  updatedAt: string
}

export interface DemoComment {
  id: number
  qaItemId: number
  parentId: number | null
  authorId: number
  content: string
  images: string[]
  /** emoji -> 반응한 멤버 id 목록 */
  reactions: Record<string, number[]>
  createdAt: string
  updatedAt: string
}

export interface DemoNotification {
  id: number
  /** 수신자 멤버 id — 알림은 로그인 사용자 기준으로 필터된다. */
  recipientId: number
  type: NotificationType
  message: string
  projectId: number | null
  qaItemId: number | null
  actorId: number | null
  read: boolean
  createdAt: string
}

export interface DemoState {
  members: DemoMember[]
  projects: DemoProject[]
  updates: DemoUpdate[]
  qa: DemoQa[]
  comments: DemoComment[]
  /** 데모는 GitHub App 이 항상 "설정된 상태" — 설치된 가짜 repo 목록. */
  githubRepos: GithubRepo[]
  /** QA id(문자열 키) → 연결된 가짜 커밋 목록. */
  githubCommits: Record<string, GithubCommit[]>
  /** 알림센터 목록 (수신자별). */
  notifications: DemoNotification[]
  /** 모든 엔티티 공용 id 시퀀스 (시드 최대 id 이후부터 증가) */
  seq: number
  /** 현재 로그인한 데모 사용자 id (없으면 미인증) */
  currentUserId: number | null
}
