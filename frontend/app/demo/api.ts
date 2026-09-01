import type {
  CommentCreateRequest,
  CommentUpdateRequest,
  ProjectCreateRequest,
  ProjectUpdateRequest,
  QaCreateRequest,
  QaPatchRequest,
  QaPriority,
  QaStatus,
  UpdateCreateRequest,
  UpdatePatchRequest,
} from '~/types/api'
import type { DemoComment, DemoProject, DemoQa, DemoUpdate } from './types'
import { DemoDb, getDemoDb } from './db'

/** ofetch 와 유사한 에러 객체. composable 들이 e.data(ApiErrorBody) / e.status 를 읽는다. */
function apiError(status: number, code: string, message: string, details?: unknown) {
  const data = { timestamp: new Date().toISOString(), status, code, message, details }
  const err = new Error(message) as Error & Record<string, unknown>
  err.status = status
  err.statusCode = status
  err.data = data
  err.response = { status, _data: data }
  return err
}

const FORBIDDEN = () =>
  apiError(403, 'DEMO_READONLY', '데모 모드에서는 사용할 수 없는 기능입니다.')

const lower = (v: string | undefined): string => (v ?? '').toLowerCase()

/* ─────────────── 로그인 OTP (데모: 코드 123456 고정) ───────────────
 * 챌린지는 인메모리 보관 — 새로고침하면 만료되어 처음부터 다시 로그인 (백엔드 만료와 동일 UX). */
const OTP_DEMO_CODE = '123456'
const OTP_MAX_ATTEMPTS = 5
const otpChallenges = new Map<string, { memberId: number; attempts: number }>()
let otpSeq = 0

function issueOtpChallenge(memberId: number): string {
  const id = `demo-otp-${++otpSeq}-${Math.random().toString(36).slice(2, 8)}`
  otpChallenges.set(id, { memberId, attempts: OTP_MAX_ATTEMPTS })
  return id
}

/** 백엔드 EmailMasker 유사 — 앞 1자만 남기고 마스킹. */
function maskEmail(email: string | null): string {
  if (!email || !email.includes('@')) return '***'
  const [local, domain] = email.split('@')
  return `${(local ?? '').slice(0, 1)}***@${domain}`
}

function requireUser(db: DemoDb) {
  const me = db.currentMember()
  if (!me) throw apiError(401, 'UNAUTHORIZED', '로그인이 필요합니다.')
  return me
}

/* ─────────────── 연쇄 삭제 ─────────────── */
function cascadeDeleteUpdates(db: DemoDb, updateIds: number[]) {
  const s = db.state
  const qaIds = s.qa.filter((q) => updateIds.includes(q.updateId)).map((q) => q.id)
  s.comments = s.comments.filter((c) => !qaIds.includes(c.qaItemId))
  s.qa = s.qa.filter((q) => !updateIds.includes(q.updateId))
  s.updates = s.updates.filter((u) => !updateIds.includes(u.id))
}

interface Ctx {
  params: string[]
  query: Record<string, unknown>
  body: any
  db: DemoDb
}
type Handler = (ctx: Ctx) => unknown

interface Route {
  method: string
  pattern: RegExp
  handler: Handler
}

/** 더 구체적인 경로를 먼저 둔다(정규식 선행 매칭). */
const ROUTES: Route[] = [
  /* ── Auth / Me ── */
  {
    method: 'POST',
    pattern: /^\/api\/auth\/login$/,
    handler: ({ body, db }) => {
      const username = String(body?.username ?? '')
      const member = db.state.members.find((m) => m.username === username)
      // 데모는 안내된 시드 계정만 허용(비밀번호는 검증하지 않음 — 실제 인증이 아님).
      if (!member) throw apiError(401, 'INVALID_CREDENTIALS', '데모 계정이 아닙니다. 안내된 계정으로 로그인하세요.')
      // OTP 체험 계정: 2단계(이메일 OTP) 화면으로 진입시킨다.
      if (member.otpEnabled) {
        return {
          authenticated: false,
          otpRequired: true,
          challengeId: issueOtpChallenge(member.id),
          maskedEmail: maskEmail(member.email),
          otpExpiresInSeconds: 300,
        }
      }
      db.state.currentUserId = member.id
      db.save()
      return { authenticated: true, expiresInSeconds: 3600, user: db.meDto(member) }
    },
  },
  {
    method: 'POST',
    pattern: /^\/api\/auth\/login\/otp\/verify$/,
    handler: ({ body, db }) => {
      const challengeId = String(body?.challengeId ?? '')
      const ch = otpChallenges.get(challengeId)
      if (!ch) throw apiError(401, 'UNAUTHORIZED', '인증 세션이 만료되었습니다. 다시 로그인해 주세요.')
      if (String(body?.code ?? '') !== OTP_DEMO_CODE) {
        ch.attempts -= 1
        if (ch.attempts <= 0) {
          otpChallenges.delete(challengeId)
          throw apiError(401, 'UNAUTHORIZED', '시도 횟수를 초과했습니다. 다시 로그인해 주세요.')
        }
        throw apiError(400, 'OTP_INVALID', '인증 코드가 올바르지 않습니다.', { remainingAttempts: ch.attempts })
      }
      otpChallenges.delete(challengeId)
      const member = db.member(ch.memberId)
      if (!member) throw apiError(401, 'UNAUTHORIZED', '인증 세션이 만료되었습니다. 다시 로그인해 주세요.')
      db.state.currentUserId = member.id
      db.save()
      return { authenticated: true, expiresInSeconds: 3600, user: db.meDto(member) }
    },
  },
  {
    method: 'POST',
    pattern: /^\/api\/auth\/login\/otp\/resend$/,
    handler: ({ body }) => {
      const challengeId = String(body?.challengeId ?? '')
      const ch = otpChallenges.get(challengeId)
      if (!ch) throw apiError(401, 'UNAUTHORIZED', '인증 세션이 만료되었습니다. 다시 로그인해 주세요.')
      otpChallenges.delete(challengeId)
      return {
        authenticated: false,
        otpRequired: true,
        challengeId: issueOtpChallenge(ch.memberId),
        otpExpiresInSeconds: 300,
      }
    },
  },
  { method: 'POST', pattern: /^\/api\/auth\/logout$/, handler: ({ db }) => { db.state.currentUserId = null; db.save(); return {} } },
  { method: 'POST', pattern: /^\/api\/auth\/refresh$/, handler: () => ({}) },
  { method: 'GET', pattern: /^\/api\/me$/, handler: ({ db }) => db.meDto(requireUser(db)) },
  {
    method: 'PATCH',
    pattern: /^\/api\/me$/,
    handler: ({ body, db }) => {
      const me = requireUser(db)
      if (typeof body?.name === 'string') me.name = body.name
      if (typeof body?.avatarUrl === 'string') me.avatarUrl = body.avatarUrl
      db.save()
      return db.meDto(me)
    },
  },
  { method: 'POST', pattern: /^\/api\/me\/password$/, handler: ({ db }) => { requireUser(db); return {} } },

  /* ── Members (데모: 읽기 전용) ── */
  { method: 'GET', pattern: /^\/api\/members$/, handler: ({ db }) => db.state.members.map((m) => db.memberDto(m)) },
  {
    method: 'GET',
    pattern: /^\/api\/members\/(\d+)$/,
    handler: ({ params, db }) => {
      const m = db.member(Number(params[0]))
      if (!m) throw apiError(404, 'NOT_FOUND', '멤버를 찾을 수 없습니다.')
      return db.memberDto(m)
    },
  },
  { method: 'POST', pattern: /^\/api\/members$/, handler: () => { throw FORBIDDEN() } },
  { method: 'PATCH', pattern: /^\/api\/members\/(\d+)$/, handler: () => { throw FORBIDDEN() } },
  { method: 'DELETE', pattern: /^\/api\/members\/(\d+)$/, handler: () => { throw FORBIDDEN() } },
  { method: 'PUT', pattern: /^\/api\/members\/(\d+)\/email$/, handler: () => { throw FORBIDDEN() } },
  { method: 'PUT', pattern: /^\/api\/members\/(\d+)\/teams-notify$/, handler: () => { throw FORBIDDEN() } },
  { method: 'POST', pattern: /^\/api\/members\/(\d+)\/teams-test$/, handler: () => { throw FORBIDDEN() } },
  { method: 'POST', pattern: /^\/api\/members\/(\d+)\/reset-password$/, handler: () => { throw FORBIDDEN() } },

  /* ── Projects ── */
  {
    method: 'GET',
    pattern: /^\/api\/projects$/,
    handler: ({ db }) => {
      const uid = db.state.currentUserId
      // 핀 우선, 그 외는 배열 순서(신규가 앞) 유지.
      const sorted = [...db.state.projects].sort((a, b) => {
        const ap = uid != null && a.pinnedBy.includes(uid) ? 1 : 0
        const bp = uid != null && b.pinnedBy.includes(uid) ? 1 : 0
        return bp - ap
      })
      return sorted.map((p) => db.projectDto(p))
    },
  },
  {
    method: 'GET',
    pattern: /^\/api\/projects\/(\d+)$/,
    handler: ({ params, db }) => {
      const p = db.state.projects.find((x) => x.id === Number(params[0]))
      if (!p) throw apiError(404, 'NOT_FOUND', '프로젝트를 찾을 수 없습니다.')
      return db.projectDto(p)
    },
  },
  {
    method: 'POST',
    pattern: /^\/api\/projects$/,
    handler: ({ body, db }) => {
      requireUser(db)
      const req = body as ProjectCreateRequest
      const now = db.nowIso()
      const p: DemoProject = {
        id: db.nextId(),
        name: req.name,
        description: req.description ?? null,
        status: (lower(req.status) || 'active') as DemoProject['status'],
        pinnedBy: [],
        githubRepos: [],
        createdAt: now,
        updatedAt: now,
      }
      db.state.projects.unshift(p)
      db.save()
      return db.projectDto(p)
    },
  },
  {
    method: 'PATCH',
    pattern: /^\/api\/projects\/(\d+)$/,
    handler: ({ params, body, db }) => {
      const p = db.state.projects.find((x) => x.id === Number(params[0]))
      if (!p) throw apiError(404, 'NOT_FOUND', '프로젝트를 찾을 수 없습니다.')
      const req = body as ProjectUpdateRequest
      if (req.name !== undefined) p.name = req.name
      if (req.description !== undefined) p.description = req.description ?? null
      if (req.status !== undefined) p.status = lower(req.status) as DemoProject['status']
      if (req.githubRepos !== undefined) {
        p.githubRepos = (req.githubRepos ?? []).map((r) => ({
          installationId: r.installationId, repoOwner: r.repoOwner, repoName: r.repoName,
        }))
      }
      p.updatedAt = db.nowIso()
      db.save()
      return db.projectDto(p)
    },
  },
  {
    method: 'DELETE',
    pattern: /^\/api\/projects\/(\d+)$/,
    handler: ({ params, db }) => {
      const id = Number(params[0])
      const updIds = db.state.updates.filter((u) => u.projectId === id).map((u) => u.id)
      cascadeDeleteUpdates(db, updIds)
      db.state.projects = db.state.projects.filter((p) => p.id !== id)
      db.save()
      return null
    },
  },
  {
    method: 'POST',
    pattern: /^\/api\/projects\/(\d+)\/pin$/,
    handler: ({ params, db }) => {
      const me = requireUser(db)
      const p = db.state.projects.find((x) => x.id === Number(params[0]))
      if (!p) throw apiError(404, 'NOT_FOUND', '프로젝트를 찾을 수 없습니다.')
      const i = p.pinnedBy.indexOf(me.id)
      if (i >= 0) p.pinnedBy.splice(i, 1)
      else p.pinnedBy.push(me.id)
      db.save()
      return { pinned: p.pinnedBy.includes(me.id) }
    },
  },

  /* ── Updates ── */
  { method: 'GET', pattern: /^\/api\/updates$/, handler: ({ db }) => [...db.state.updates].sort((a, b) => a.sortOrder - b.sortOrder).map((u) => db.updateDto(u)) },
  {
    method: 'GET',
    pattern: /^\/api\/projects\/(\d+)\/updates$/,
    handler: ({ params, db }) =>
      db.state.updates
        .filter((u) => u.projectId === Number(params[0]))
        .sort((a, b) => a.sortOrder - b.sortOrder)
        .map((u) => db.updateDto(u)),
  },
  {
    method: 'POST',
    pattern: /^\/api\/projects\/(\d+)\/updates$/,
    handler: ({ params, body, db }) => {
      requireUser(db)
      const projectId = Number(params[0])
      const req = body as UpdateCreateRequest
      const maxOrder = db.state.updates
        .filter((u) => u.projectId === projectId)
        .reduce((mx, u) => Math.max(mx, u.sortOrder), -1)
      const now = db.nowIso()
      const u: DemoUpdate = {
        id: db.nextId(),
        projectId,
        version: req.version,
        title: req.title,
        description: req.description ?? null,
        status: (lower(req.status) || 'in_progress') as DemoUpdate['status'],
        sortOrder: maxOrder + 1,
        createdAt: now,
        updatedAt: now,
      }
      db.state.updates.push(u)
      db.save()
      return db.updateDto(u)
    },
  },
  {
    method: 'PUT',
    pattern: /^\/api\/projects\/(\d+)\/updates\/order$/,
    handler: ({ params, body, db }) => {
      const projectId = Number(params[0])
      const ids: number[] = Array.isArray(body?.updateIds) ? body.updateIds : []
      ids.forEach((id, idx) => {
        const u = db.state.updates.find((x) => x.id === id && x.projectId === projectId)
        if (u) u.sortOrder = idx
      })
      db.save()
      return db.state.updates
        .filter((u) => u.projectId === projectId)
        .sort((a, b) => a.sortOrder - b.sortOrder)
        .map((u) => db.updateDto(u))
    },
  },
  { method: 'GET', pattern: /^\/api\/updates\/(\d+)$/, handler: ({ params, db }) => {
    const u = db.state.updates.find((x) => x.id === Number(params[0]))
    if (!u) throw apiError(404, 'NOT_FOUND', '업데이트를 찾을 수 없습니다.')
    return db.updateDto(u)
  } },
  {
    method: 'PATCH',
    pattern: /^\/api\/updates\/(\d+)$/,
    handler: ({ params, body, db }) => {
      const u = db.state.updates.find((x) => x.id === Number(params[0]))
      if (!u) throw apiError(404, 'NOT_FOUND', '업데이트를 찾을 수 없습니다.')
      const req = body as UpdatePatchRequest
      if (req.version !== undefined) u.version = req.version
      if (req.title !== undefined) u.title = req.title
      if (req.description !== undefined) u.description = req.description ?? null
      if (req.status !== undefined) u.status = lower(req.status) as DemoUpdate['status']
      u.updatedAt = db.nowIso()
      db.save()
      return db.updateDto(u)
    },
  },
  {
    method: 'DELETE',
    pattern: /^\/api\/updates\/(\d+)$/,
    handler: ({ params, db }) => {
      cascadeDeleteUpdates(db, [Number(params[0])])
      db.save()
      return null
    },
  },

  /* ── QA ── */
  {
    method: 'GET',
    pattern: /^\/api\/qa$/,
    handler: ({ query, db }) => {
      let list = db.state.qa
      if (query.updateId != null && query.updateId !== '') list = list.filter((q) => q.updateId === Number(query.updateId))
      if (query.status) list = list.filter((q) => q.status === lower(String(query.status)))
      if (query.priority) list = list.filter((q) => q.priority === lower(String(query.priority)))
      if (query.assigneeId != null && query.assigneeId !== '') {
        const aid = Number(query.assigneeId)
        list = list.filter((q) => q.assignee1Id === aid || q.assignee2Id === aid)
      }
      if (query.testerId != null && query.testerId !== '') list = list.filter((q) => q.testerId === Number(query.testerId))
      return list.map((q) => db.qaDto(q))
    },
  },
  {
    method: 'GET',
    pattern: /^\/api\/qa\/page$/,
    handler: ({ query, db }) => {
      const size = [10, 50, 100].includes(Number(query.size)) ? Number(query.size) : 10
      const page = Math.max(0, Number(query.page) || 0)
      const list = db.state.qa
      return {
        content: list.slice(page * size, page * size + size).map((q) => db.qaDto(q)),
        page,
        size,
        totalElements: list.length,
        totalPages: Math.ceil(list.length / size),
      }
    },
  },
  {
    method: 'GET',
    pattern: /^\/api\/qa\/dashboard-stats$/,
    handler: ({ query, db }) => {
      const uid = String(query.mine) === 'true' ? requireUser(db).id : null
      const list = uid == null
        ? db.state.qa
        : db.state.qa.filter((q) => q.testerId === uid || q.assignee1Id === uid || q.assignee2Id === uid)
      const count = (s: QaStatus) => list.filter((q) => q.status === s).length
      const byProject = new Map<number, { count: number; resolved: number }>()
      for (const q of list) {
        const upd = db.state.updates.find((u) => u.id === q.updateId)
        if (!upd) continue
        const s = byProject.get(upd.projectId) ?? { count: 0, resolved: 0 }
        s.count += 1
        if (q.status === 'fix_done' || q.status === 'confirmed') s.resolved += 1
        byProject.set(upd.projectId, s)
      }
      return {
        total: list.length,
        needsFix: count('needs_fix'),
        inProgress: count('in_progress'),
        fixDone: count('fix_done'),
        confirmed: count('confirmed'),
        onHold: count('on_hold'),
        needsRecheck: count('needs_recheck'),
        critical: list.filter((q) => q.priority === 'critical').length,
        byProject: [...byProject].map(([projectId, v]) => ({ projectId, ...v })),
      }
    },
  },
  {
    method: 'POST',
    pattern: /^\/api\/qa$/,
    handler: ({ body, db }) => {
      const me = requireUser(db)
      const req = body as QaCreateRequest
      const now = db.nowIso()
      const q: DemoQa = {
        id: db.nextId(),
        updateId: req.updateId,
        title: req.title,
        description: req.description ?? null,
        category: req.category ?? null,
        status: (lower(req.status) || 'needs_fix') as QaStatus,
        // tester 미지정 시 현재 사용자 자동 지정(백엔드 규칙).
        testerId: req.testerId ?? me.id,
        assignee1Id: req.assignee1Id ?? null,
        assignee2Id: req.assignee2Id ?? null,
        priority: (lower(req.priority) || 'medium') as QaPriority,
        images: req.images ?? [],
        githubIssue: null,
        createdAt: now,
        updatedAt: now,
      }
      // createGithubIssue: 프로젝트에 repo 가 연결돼 있으면 가짜 이슈를 만들어 연결한다.
      // 지정 repo 가 연결 목록에 있으면 그 repo, 아니면 첫 번째 연결 repo (백엔드 규칙과 동일).
      if (req.createGithubIssue) {
        const upd = db.state.updates.find((u) => u.id === req.updateId)
        const proj = upd ? db.state.projects.find((p) => p.id === upd.projectId) : undefined
        const repos = proj?.githubRepos ?? []
        const repo = repos.find((r) => r.repoOwner === req.githubRepoOwner && r.repoName === req.githubRepoName) ?? repos[0]
        if (repo) {
          const issueNumber = db.nextId()
          q.githubIssue = {
            issueNumber,
            issueUrl: `https://github.com/${repo.repoOwner}/${repo.repoName}/issues/${issueNumber}`,
            state: 'open',
            repoOwner: repo.repoOwner,
            repoName: repo.repoName,
          }
        }
      }
      db.state.qa.unshift(q)
      db.save()
      return db.qaDto(q)
    },
  },
  { method: 'GET', pattern: /^\/api\/qa\/(\d+)$/, handler: ({ params, db }) => {
    const q = db.state.qa.find((x) => x.id === Number(params[0]))
    if (!q) throw apiError(404, 'NOT_FOUND', 'QA 항목을 찾을 수 없습니다.')
    return db.qaDto(q)
  } },
  {
    method: 'PATCH',
    pattern: /^\/api\/qa\/(\d+)$/,
    handler: ({ params, body, db }) => {
      const q = db.state.qa.find((x) => x.id === Number(params[0]))
      if (!q) throw apiError(404, 'NOT_FOUND', 'QA 항목을 찾을 수 없습니다.')
      const req = body as QaPatchRequest
      if (req.updateId !== undefined) q.updateId = req.updateId
      if (req.title !== undefined) q.title = req.title
      if (req.description !== undefined) q.description = req.description ?? null
      if (req.category !== undefined) q.category = req.category ?? null
      if (req.status !== undefined) q.status = lower(req.status) as QaStatus
      if (req.priority !== undefined) q.priority = lower(req.priority) as QaPriority
      if (req.images !== undefined) q.images = req.images ?? []
      if (req.clearTester) q.testerId = null
      else if (req.testerId !== undefined) q.testerId = req.testerId
      if (req.clearAssignee1) q.assignee1Id = null
      else if (req.assignee1Id !== undefined) q.assignee1Id = req.assignee1Id
      if (req.clearAssignee2) q.assignee2Id = null
      else if (req.assignee2Id !== undefined) q.assignee2Id = req.assignee2Id
      q.updatedAt = db.nowIso()
      db.save()
      return db.qaDto(q)
    },
  },
  {
    method: 'DELETE',
    pattern: /^\/api\/qa\/(\d+)$/,
    handler: ({ params, db }) => {
      const id = Number(params[0])
      db.state.comments = db.state.comments.filter((c) => c.qaItemId !== id)
      db.state.qa = db.state.qa.filter((q) => q.id !== id)
      db.save()
      return null
    },
  },
  { method: 'GET', pattern: /^\/api\/qa\/(\d+)\/history$/, handler: () => [] },

  /* ── Comments ── */
  {
    method: 'GET',
    pattern: /^\/api\/qa\/(\d+)\/comments$/,
    handler: ({ params, db }) =>
      db.state.comments
        .filter((c) => c.qaItemId === Number(params[0]))
        .sort((a, b) => a.createdAt.localeCompare(b.createdAt))
        .map((c) => db.commentDto(c)),
  },
  {
    method: 'POST',
    pattern: /^\/api\/qa\/(\d+)\/comments$/,
    handler: ({ params, body, db }) => {
      const me = requireUser(db)
      const req = body as CommentCreateRequest
      const now = db.nowIso()
      const c: DemoComment = {
        id: db.nextId(),
        qaItemId: Number(params[0]),
        parentId: req.parentId ?? null,
        authorId: me.id,
        content: req.content,
        images: req.images ?? [],
        reactions: {},
        createdAt: now,
        updatedAt: now,
      }
      db.state.comments.push(c)
      db.save()
      return db.commentDto(c)
    },
  },
  {
    method: 'PATCH',
    pattern: /^\/api\/comments\/(\d+)$/,
    handler: ({ params, body, db }) => {
      const c = db.state.comments.find((x) => x.id === Number(params[0]))
      if (!c) throw apiError(404, 'NOT_FOUND', '댓글을 찾을 수 없습니다.')
      const req = body as CommentUpdateRequest
      if (req.content !== undefined) c.content = req.content
      if (req.images !== undefined) c.images = req.images ?? []
      c.updatedAt = db.nowIso()
      db.save()
      return db.commentDto(c)
    },
  },
  {
    method: 'DELETE',
    pattern: /^\/api\/comments\/(\d+)$/,
    handler: ({ params, db }) => {
      const id = Number(params[0])
      // 대댓글까지 함께 제거.
      db.state.comments = db.state.comments.filter((c) => c.id !== id && c.parentId !== id)
      db.save()
      return null
    },
  },
  {
    method: 'POST',
    pattern: /^\/api\/comments\/(\d+)\/reactions$/,
    handler: ({ params, body, db }) => {
      const me = requireUser(db)
      const c = db.state.comments.find((x) => x.id === Number(params[0]))
      if (!c) throw apiError(404, 'NOT_FOUND', '댓글을 찾을 수 없습니다.')
      const emoji = String(body?.emoji ?? '')
      if (emoji) {
        const arr = c.reactions[emoji] ?? []
        const i = arr.indexOf(me.id)
        if (i >= 0) arr.splice(i, 1)
        else arr.push(me.id)
        if (arr.length) c.reactions[emoji] = arr
        else delete c.reactions[emoji]
      }
      db.save()
      return db.commentDto(c)
    },
  },

  /* ── GitHub (데모: 항상 "설정된 상태"로 시드, 설정 변경은 불가) ── */
  {
    method: 'GET',
    pattern: /^\/api\/github\/app$/,
    handler: () => ({
      configured: true,
      appSlug: 'qa-manager-demo',
      appName: 'QA Manager Demo',
      installUrl: 'https://github.com/apps/qa-manager-demo/installations/new',
    }),
  },
  { method: 'POST', pattern: /^\/api\/github\/app\/manifest$/, handler: () => { throw FORBIDDEN() } },
  { method: 'POST', pattern: /^\/api\/github\/app\/conversion$/, handler: () => { throw FORBIDDEN() } },
  { method: 'DELETE', pattern: /^\/api\/github\/app$/, handler: () => { throw FORBIDDEN() } },
  { method: 'GET', pattern: /^\/api\/github\/repos$/, handler: ({ db }) => db.state.githubRepos ?? [] },
  {
    method: 'GET',
    pattern: /^\/api\/github\/qa\/(\d+)\/commits$/,
    handler: ({ params, db }) => {
      const q = db.state.qa.find((x) => x.id === Number(params[0]))
      if (!q) throw apiError(404, 'NOT_FOUND', 'QA 항목을 찾을 수 없습니다.')
      // 이슈 미연결 QA 는 빈 배열 (백엔드 규칙과 동일).
      if (!q.githubIssue) return []
      return db.state.githubCommits?.[String(q.id)] ?? []
    },
  },

  /* ── Notifications (수신자 = 로그인 사용자 기준) ── */
  {
    method: 'GET',
    pattern: /^\/api\/notifications$/,
    handler: ({ db }) => {
      const me = requireUser(db)
      return (db.state.notifications ?? [])
        .filter((n) => n.recipientId === me.id)
        .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
        .map((n) => db.notificationDto(n))
    },
  },
  {
    method: 'PATCH',
    pattern: /^\/api\/notifications\/(\d+)\/read$/,
    handler: ({ params, db }) => {
      const me = requireUser(db)
      const n = (db.state.notifications ?? []).find(
        (x) => x.id === Number(params[0]) && x.recipientId === me.id,
      )
      if (!n) throw apiError(404, 'NOT_FOUND', '알림을 찾을 수 없습니다.')
      n.read = true
      db.save()
      return db.notificationDto(n)
    },
  },
  {
    method: 'PATCH',
    pattern: /^\/api\/notifications\/read-all$/,
    handler: ({ db }) => {
      const me = requireUser(db)
      for (const n of db.state.notifications ?? []) {
        if (n.recipientId === me.id) n.read = true
      }
      db.save()
      return { unread: 0 }
    },
  },

  /* ── Files (데모: 업로드는 useUpload 에서 data URL 로 우회) ── */
  { method: 'POST', pattern: /^\/api\/files\/presigned$/, handler: () => { throw FORBIDDEN() } },
]

/**
 * 데모용 $api 대체 함수. plugins/api.ts 에서 데모 모드일 때 $fetch 대신 주입한다.
 * composable 들이 호출하는 `api(url, { method, body, query })` 시그니처를 따른다.
 */
export function createDemoApi() {
  const impl = (request: unknown, opts: Record<string, any> = {}) => {
    const db = getDemoDb()
    const rawUrl = typeof request === 'string' ? request : String((request as any)?.url ?? request)
    const path = (rawUrl.split('?')[0] ?? '').replace(/\/+$/, '') || '/'
    const method = String(opts.method ?? 'GET').toUpperCase()
    const query = (opts.query ?? opts.params ?? {}) as Record<string, unknown>
    const body = opts.body

    for (const r of ROUTES) {
      if (r.method !== method) continue
      const m = r.pattern.exec(path)
      if (!m) continue
      // 비동기 시그니처 유지(호출부가 await 한다).
      return Promise.resolve(r.handler({ params: m.slice(1), query, body, db }))
    }
    return Promise.reject(apiError(404, 'NOT_FOUND', `데모 mock 미구현 경로: ${method} ${path}`))
  }
  return impl
}
