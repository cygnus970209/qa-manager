import type {
  AssigneeSummary,
  Me,
  Member,
  Project,
  ProjectUpdate,
  QaComment,
  QaItem,
} from '~/types/api'
import type {
  DemoComment,
  DemoMember,
  DemoProject,
  DemoQa,
  DemoState,
  DemoUpdate,
} from './types'
import { createSeed } from './seed'

// v2: GitHub 연동 데모 데이터(repo/issue/commit) 추가 — 구버전 상태는 버리고 재시드한다.
const LS_KEY = 'qa-demo-state-v2'

/**
 * 데모용 인메모리 + localStorage 백엔드.
 * - 상태 전체를 직렬화해 localStorage 에 저장하므로 새로고침/재방문에도 변경이 유지된다.
 * - 브라우저(localStorage)별로 격리되므로 다른 방문자/서버에는 절대 반영되지 않는다.
 * - 클라이언트에서만 인스턴스화된다(getDemoDb).
 */
export class DemoDb {
  state: DemoState

  constructor() {
    this.state = this.load()
  }

  private load(): DemoState {
    try {
      const raw = localStorage.getItem(LS_KEY)
      if (raw) return JSON.parse(raw) as DemoState
    } catch {
      /* 파싱 실패 시 시드로 폴백 */
    }
    const seed = createSeed()
    this.persist(seed)
    return seed
  }

  private persist(state: DemoState) {
    try {
      localStorage.setItem(LS_KEY, JSON.stringify(state))
    } catch {
      /* 용량 초과 등 — 데모이므로 무시 */
    }
  }

  save() {
    this.persist(this.state)
  }

  /** 시드 상태로 초기화. 로그인 사용자는 유지하지 않는다(완전 초기화). */
  reset() {
    this.state = createSeed()
    this.save()
  }

  nextId(): number {
    this.state.seq += 1
    return this.state.seq
  }

  nowIso(): string {
    // 데모 표시는 로컬 기준이면 충분. ISO 문자열로 저장(백엔드 Jackson 과 동일 포맷).
    return new Date().toISOString()
  }

  /* ─────────────── 조회 헬퍼 ─────────────── */
  member(id: number | null): DemoMember | undefined {
    if (id == null) return undefined
    return this.state.members.find((m) => m.id === id)
  }

  currentMember(): DemoMember | undefined {
    return this.member(this.state.currentUserId)
  }

  /* ─────────────── DTO 변환 ─────────────── */
  assigneeSummary(id: number | null): AssigneeSummary | null {
    const m = this.member(id)
    if (!m) return null
    return { id: m.id, name: m.name, avatarUrl: m.avatarUrl }
  }

  meDto(m: DemoMember): Me {
    return { id: m.id, username: m.username, name: m.name, role: m.role, avatarUrl: m.avatarUrl }
  }

  memberDto(m: DemoMember): Member {
    return {
      id: m.id,
      username: m.username,
      name: m.name,
      email: m.email,
      role: m.role,
      avatarUrl: m.avatarUrl,
      teamsLinked: false,
      teamsNotifyEnabled: true,
    }
  }

  projectDto(p: DemoProject): Project {
    const uid = this.state.currentUserId
    return {
      id: p.id,
      name: p.name,
      description: p.description,
      status: p.status,
      pinned: uid != null && p.pinnedBy.includes(uid),
      createdAt: p.createdAt,
      updatedAt: p.updatedAt,
      githubInstallationId: p.githubInstallationId ?? null,
      githubRepoOwner: p.githubRepoOwner ?? null,
      githubRepoName: p.githubRepoName ?? null,
    }
  }

  updateDto(u: DemoUpdate): ProjectUpdate {
    return {
      id: u.id,
      projectId: u.projectId,
      version: u.version,
      title: u.title,
      description: u.description,
      status: u.status,
      createdAt: u.createdAt,
      updatedAt: u.updatedAt,
    }
  }

  qaDto(q: DemoQa): QaItem {
    return {
      id: q.id,
      updateId: q.updateId,
      title: q.title,
      description: q.description,
      category: q.category,
      status: q.status,
      tester: this.assigneeSummary(q.testerId),
      assignee1: this.assigneeSummary(q.assignee1Id),
      assignee2: this.assigneeSummary(q.assignee2Id),
      priority: q.priority,
      images: q.images,
      githubIssue: q.githubIssue ?? null,
      createdAt: q.createdAt,
      updatedAt: q.updatedAt,
    }
  }

  commentDto(c: DemoComment): QaComment {
    const author = this.assigneeSummary(c.authorId) ?? { id: c.authorId, name: '알 수 없음', avatarUrl: null }
    return {
      id: c.id,
      qaItemId: c.qaItemId,
      parentId: c.parentId,
      author,
      content: c.content,
      images: c.images,
      reactions: c.reactions,
      createdAt: c.createdAt,
      updatedAt: c.updatedAt,
    }
  }
}

let _db: DemoDb | null = null

/** 클라이언트 전용 싱글톤. 서버(빌드 prerender)에서는 호출되지 않아야 한다. */
export function getDemoDb(): DemoDb {
  if (!_db) _db = new DemoDb()
  return _db
}
