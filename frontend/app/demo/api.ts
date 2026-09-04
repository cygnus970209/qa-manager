import type {
  CommentCreateRequest,
  CommentUpdateRequest,
  FlowGraph,
  ProjectCreateRequest,
  ProjectUpdateRequest,
  QaCreateRequest,
  QaPatchRequest,
  QaPriority,
  QaStatus,
  TestCaseCreateRequest,
  TestCaseUpdateRequest,
  TestRunCaseResult,
  UpdateCreateRequest,
  UpdatePatchRequest,
} from '~/types/api'
import { useNuxtApp } from '#app'
import type { DemoComment, DemoProject, DemoQa, DemoTestCase, DemoTestFlow, DemoTestRun, DemoTestRunCase, DemoTestSuite, DemoUpdate } from './types'
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

/** 호출 시점에 i18n 에 lazy 접근. nuxt 컨텍스트 밖(테스트 등)에서는 한국어 폴백. */
function tr(key: string, fallback: string, params?: Record<string, unknown>): string {
  try {
    const { $i18n } = useNuxtApp() as any
    if ($i18n?.t) return params ? $i18n.t(key, params) : $i18n.t(key)
  } catch { /* nuxt 컨텍스트 밖 */ }
  return fallback
}

const FORBIDDEN = () =>
  apiError(403, 'DEMO_READONLY', tr('demo.api.readonly', '데모 모드에서는 사용할 수 없는 기능입니다.'))

// 테스트 엔티티(스위트/케이스/플로우/런) 404 — 신규 i18n 키 없이 근사 키(qaNotFound)를 재사용.
const TESTING_NOT_FOUND = () =>
  apiError(404, 'NOT_FOUND', tr('demo.api.qaNotFound', 'QA 항목을 찾을 수 없습니다.'))

const lower = (v: string | undefined): string => (v ?? '').toLowerCase()

/* ─────────────── 로그인 OTP (데모: 코드 123456 고정) ───────────────
 * 챌린지는 인메모리 보관 — 새로고침하면 만료되어 처음부터 다시 로그인 (백엔드 만료와 동일 UX). */
const OTP_DEMO_CODE = '123456'

/** 데모 통합 검색 — 서버의 search_document 와 같은 종류·정렬(제목 일치 → 본문 일치)을 부분 일치로 흉내 낸다 */
function demoSearch(db: DemoDb, q: string, types: string[], projectId: number | null, page: number, size: number) {
  const query = q.trim().toLowerCase()
  const words = query.split(/[^\p{L}\p{N}]+/u).filter((w) => w.length > 0)
  type Doc = { type: string; id: number; title: string; body: string; projectId: number | null; updateId: number | null; qaItemId: number | null; status: string | null; updatedAt: string | null }
  const docs: Doc[] = []
  const projectOfUpdate = (updateId: number) => db.state.updates.find((u) => u.id === updateId)?.projectId ?? null
  for (const p of db.state.projects) docs.push({ type: 'project', id: p.id, title: p.name, body: p.description ?? '', projectId: p.id, updateId: null, qaItemId: null, status: p.status, updatedAt: p.updatedAt })
  for (const u of db.state.updates) docs.push({ type: 'update', id: u.id, title: `${u.version} ${u.title}`, body: u.description ?? '', projectId: u.projectId, updateId: u.id, qaItemId: null, status: u.status, updatedAt: u.updatedAt })
  for (const x of db.state.qa) docs.push({ type: 'qa', id: x.id, title: x.title, body: (x.category ? `[${x.category}] ` : '') + (x.description ?? ''), projectId: projectOfUpdate(x.updateId), updateId: x.updateId, qaItemId: x.id, status: x.status, updatedAt: x.updatedAt })
  for (const c of db.state.comments) {
    const x = db.state.qa.find((y) => y.id === c.qaItemId)
    if (!x) continue
    docs.push({ type: 'comment', id: c.id, title: `#${x.id} ${x.title}`, body: c.content, projectId: projectOfUpdate(x.updateId), updateId: x.updateId, qaItemId: x.id, status: x.status, updatedAt: c.updatedAt })
  }
  for (const tc of db.state.testCases) docs.push({ type: 'test_case', id: tc.id, title: tc.title, body: [tc.precondition ?? '', ...tc.steps.map((st) => `${st.action} → ${st.expected}`)].join('\n'), projectId: tc.projectId, updateId: null, qaItemId: null, status: tc.priority, updatedAt: tc.updatedAt })
  const matches = (d: Doc) => {
    if (words.length === 0) return false
    const text = `${d.title}\n${d.body}`.toLowerCase()
    return words.every((w) => text.includes(w))
  }
  let hits = docs.filter((d) => matches(d) && (projectId == null || d.projectId === projectId))
  const digits = query.startsWith('#') ? query.slice(1) : query
  if (/^\d{1,9}$/.test(digits)) {
    const direct = docs.find((d) => d.type === 'qa' && d.id === Number(digits) && (projectId == null || d.projectId === projectId))
    if (direct) hits = [direct, ...hits.filter((d) => !(d.type === 'qa' && d.id === direct.id))]
  }
  const counts: Record<string, number> = { qa: 0, comment: 0, project: 0, update: 0, test_case: 0 }
  for (const d of hits) counts[d.type] = (counts[d.type] ?? 0) + 1
  const filtered = types.length > 0 ? hits.filter((d) => types.includes(d.type)) : hits
  const titleHit = (d: Doc) => (d.title.toLowerCase().includes(query) ? 1 : 0)
  filtered.sort((a, b) => titleHit(b) - titleHit(a) || String(b.updatedAt ?? '').localeCompare(String(a.updatedAt ?? '')))
  const snippet = (body: string) => {
    const flat = body.replace(/\s+/g, ' ').trim()
    const at = words.map((w) => flat.toLowerCase().indexOf(w)).find((i) => i >= 0) ?? -1
    if (at < 0) return flat.length > 120 ? flat.slice(0, 120) + '…' : flat
    const start = Math.max(0, at - 60)
    return (start > 0 ? '…' : '') + flat.slice(start, at + 120) + (at + 120 < flat.length ? '…' : '')
  }
  const items = filtered.slice(page * size, page * size + size).map((d) => ({
    type: d.type, id: d.id, title: d.title, snippet: snippet(d.body), projectId: d.projectId,
    projectName: db.state.projects.find((p) => p.id === d.projectId)?.name ?? null,
    updateId: d.updateId, qaItemId: d.qaItemId, status: d.status, updatedAt: d.updatedAt,
  }))
  return { query: q, total: filtered.length, counts, items, page, size }
}

/** 프로젝트 정렬: 핀 우선 → 사용자별 저장 순서 → 배열 순서(신규가 앞). 서버 ProjectService.list 와 같은 규칙 */
function sortedProjects(db: DemoDb) {
  const uid = db.state.currentUserId
  const order = (uid != null && db.state.projectOrder?.[String(uid)]) || []
  const rank = new Map(order.map((id, i) => [id, i]))
  return [...db.state.projects].sort((a, b) => {
    const ap = uid != null && a.pinnedBy.includes(uid) ? 1 : 0
    const bp = uid != null && b.pinnedBy.includes(uid) ? 1 : 0
    if (ap !== bp) return bp - ap
    return (rank.get(a.id) ?? Infinity) - (rank.get(b.id) ?? Infinity)
  })
}
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
  if (!me) throw apiError(401, 'UNAUTHORIZED', tr('demo.api.loginRequired', '로그인이 필요합니다.'))
  return me
}

function requireAdmin(db: DemoDb) {
  const me = requireUser(db)
  if (me.accountRole !== 'ADMIN') {
    throw apiError(403, 'FORBIDDEN', tr('demo.api.adminOnly', '관리자만 사용할 수 있는 기능입니다.'))
  }
  return me
}

/* ─────────────── 연쇄 삭제 ─────────────── */
function cascadeDeleteUpdates(db: DemoDb, updateIds: number[]) {
  const s = db.state
  const qaIds = s.qa.filter((q) => updateIds.includes(q.updateId)).map((q) => q.id)
  s.comments = s.comments.filter((c) => !qaIds.includes(c.qaItemId))
  s.qa = s.qa.filter((q) => !updateIds.includes(q.updateId))
  s.updates = s.updates.filter((u) => !updateIds.includes(u.id))
  // 테스트 런은 업데이트 소속 — 함께 제거하고, 플로우는 연결만 해제.
  const runIds = s.testRuns.filter((r) => updateIds.includes(r.updateId)).map((r) => r.id)
  s.testRunCases = s.testRunCases.filter((c) => !runIds.includes(c.runId))
  s.testRuns = s.testRuns.filter((r) => !updateIds.includes(r.updateId))
  for (const f of s.testFlows) {
    if (f.updateId != null && updateIds.includes(f.updateId)) f.updateId = null
  }
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
      if (!member) throw apiError(401, 'INVALID_CREDENTIALS', tr('demo.api.invalidAccount', '데모 계정이 아닙니다. 안내된 계정으로 로그인하세요.'))
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
      if (!ch) throw apiError(401, 'UNAUTHORIZED', tr('demo.api.otpSessionExpired', '인증 세션이 만료되었습니다. 다시 로그인해 주세요.'))
      if (String(body?.code ?? '') !== OTP_DEMO_CODE) {
        ch.attempts -= 1
        if (ch.attempts <= 0) {
          otpChallenges.delete(challengeId)
          throw apiError(401, 'UNAUTHORIZED', tr('demo.api.otpTooManyAttempts', '시도 횟수를 초과했습니다. 다시 로그인해 주세요.'))
        }
        throw apiError(400, 'OTP_INVALID', tr('demo.api.otpInvalidCode', '인증 코드가 올바르지 않습니다.'), { remainingAttempts: ch.attempts })
      }
      otpChallenges.delete(challengeId)
      const member = db.member(ch.memberId)
      if (!member) throw apiError(401, 'UNAUTHORIZED', tr('demo.api.otpSessionExpired', '인증 세션이 만료되었습니다. 다시 로그인해 주세요.'))
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
      if (!ch) throw apiError(401, 'UNAUTHORIZED', tr('demo.api.otpSessionExpired', '인증 세션이 만료되었습니다. 다시 로그인해 주세요.'))
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
      if (!m) throw apiError(404, 'NOT_FOUND', tr('demo.api.memberNotFound', '멤버를 찾을 수 없습니다.'))
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
  // 계정 권한 변경 — 데모에서도 실제로 동작 (이 브라우저의 localStorage 에만 반영).
  {
    method: 'PUT',
    pattern: /^\/api\/members\/(\d+)\/account-role$/,
    handler: ({ db, params, body }) => {
      const me = requireAdmin(db)
      const id = Number(params[0])
      if (me.id === id) {
        throw apiError(400, 'BAD_REQUEST', tr('demo.api.cannotChangeOwnRole', '자신의 권한은 변경할 수 없습니다.'))
      }
      const m = db.state.members.find((x) => x.id === id)
      if (!m) throw apiError(404, 'NOT_FOUND', tr('demo.api.memberNotFound', '멤버를 찾을 수 없습니다.'))
      const next = body?.accountRole
      if (next !== 'ADMIN' && next !== 'MEMBER') {
        throw apiError(400, 'BAD_REQUEST', tr('demo.api.invalidAccountRole', '올바르지 않은 권한 값입니다.'))
      }
      m.accountRole = next
      db.save()
      return db.memberDto(m)
    },
  },

  /* ── Projects ── */
  /* ── Search ── */
  {
    method: 'GET',
    pattern: /^\/api\/search$/,
    handler: ({ query, db }) => {
      requireUser(db)
      const types = typeof query.types === 'string' && query.types ? String(query.types).split(',') : []
      const projectId = query.projectId != null && query.projectId !== '' ? Number(query.projectId) : null
      const size = Math.min(Math.max(Number(query.size) || 20, 1), 50)
      return demoSearch(db, String(query.q ?? ''), types, projectId, Math.max(0, Number(query.page) || 0), size)
    },
  },
  {
    method: 'GET',
    pattern: /^\/api\/search\/stats$/,
    handler: ({ db }) => {
      requireUser(db)
      const counts = { qa: db.state.qa.length, comment: db.state.comments.length, project: db.state.projects.length, update: db.state.updates.length, test_case: db.state.testCases.length }
      return { counts, total: Object.values(counts).reduce((a, b) => a + b, 0) }
    },
  },
  {
    method: 'POST',
    pattern: /^\/api\/search\/reindex$/,
    handler: ({ db }) => {
      requireUser(db)
      const counts = { qa: db.state.qa.length, comment: db.state.comments.length, project: db.state.projects.length, update: db.state.updates.length, test_case: db.state.testCases.length }
      return { counts, total: Object.values(counts).reduce((a, b) => a + b, 0) }
    },
  },
  {
    method: 'GET',
    pattern: /^\/api\/projects$/,
    handler: ({ db }) => {
      return sortedProjects(db).map((p) => db.projectDto(p))
    },
  },
  {
    method: 'PUT',
    pattern: /^\/api\/projects\/order$/,
    handler: ({ body, db }) => {
      const me = requireUser(db)
      const ids = ((body?.projectIds ?? []) as number[]).filter((id) => db.state.projects.some((p) => p.id === id))
      db.state.projectOrder = { ...(db.state.projectOrder ?? {}), [String(me.id)]: ids }
      db.save()
      return sortedProjects(db).map((p) => db.projectDto(p))
    },
  },
  {
    method: 'GET',
    pattern: /^\/api\/projects\/(\d+)$/,
    handler: ({ params, db }) => {
      const p = db.state.projects.find((x) => x.id === Number(params[0]))
      if (!p) throw apiError(404, 'NOT_FOUND', tr('demo.api.projectNotFound', '프로젝트를 찾을 수 없습니다.'))
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
      if (!p) throw apiError(404, 'NOT_FOUND', tr('demo.api.projectNotFound', '프로젝트를 찾을 수 없습니다.'))
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
      // 프로젝트 소속 테스트 자산(스위트/케이스/플로우)도 함께 제거.
      db.state.testSuites = db.state.testSuites.filter((s) => s.projectId !== id)
      db.state.testCases = db.state.testCases.filter((t) => t.projectId !== id)
      db.state.testFlows = db.state.testFlows.filter((f) => f.projectId !== id)
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
      if (!p) throw apiError(404, 'NOT_FOUND', tr('demo.api.projectNotFound', '프로젝트를 찾을 수 없습니다.'))
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
    if (!u) throw apiError(404, 'NOT_FOUND', tr('demo.api.updateNotFound', '업데이트를 찾을 수 없습니다.'))
    return db.updateDto(u)
  } },
  {
    method: 'PATCH',
    pattern: /^\/api\/updates\/(\d+)$/,
    handler: ({ params, body, db }) => {
      const u = db.state.updates.find((x) => x.id === Number(params[0]))
      if (!u) throw apiError(404, 'NOT_FOUND', tr('demo.api.updateNotFound', '업데이트를 찾을 수 없습니다.'))
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
      const byProject = new Map<number, { count: number; resolved: number; needsFix: number }>()
      for (const q of list) {
        const upd = db.state.updates.find((u) => u.id === q.updateId)
        if (!upd) continue
        const s = byProject.get(upd.projectId) ?? { count: 0, resolved: 0, needsFix: 0 }
        s.count += 1
        if (q.status === 'fix_done' || q.status === 'confirmed') s.resolved += 1
        if (q.status === 'needs_fix') s.needsFix += 1
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
    if (!q) throw apiError(404, 'NOT_FOUND', tr('demo.api.qaNotFound', 'QA 항목을 찾을 수 없습니다.'))
    return db.qaDto(q)
  } },
  {
    method: 'PATCH',
    pattern: /^\/api\/qa\/(\d+)$/,
    handler: ({ params, body, db }) => {
      const q = db.state.qa.find((x) => x.id === Number(params[0]))
      if (!q) throw apiError(404, 'NOT_FOUND', tr('demo.api.qaNotFound', 'QA 항목을 찾을 수 없습니다.'))
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
      if (!c) throw apiError(404, 'NOT_FOUND', tr('demo.api.commentNotFound', '댓글을 찾을 수 없습니다.'))
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
      if (!c) throw apiError(404, 'NOT_FOUND', tr('demo.api.commentNotFound', '댓글을 찾을 수 없습니다.'))
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

  /* ── 테스트 케이스 관리: 스위트 ── */
  {
    method: 'GET',
    pattern: /^\/api\/projects\/(\d+)\/test-suites$/,
    handler: ({ params, db }) => {
      requireUser(db)
      return db.state.testSuites
        .filter((s) => s.projectId === Number(params[0]))
        .sort((a, b) => a.sortOrder - b.sortOrder)
    },
  },
  {
    method: 'POST',
    pattern: /^\/api\/projects\/(\d+)\/test-suites$/,
    handler: ({ params, body, db }) => {
      requireUser(db)
      const projectId = Number(params[0])
      const maxOrder = db.state.testSuites
        .filter((s) => s.projectId === projectId)
        .reduce((mx, s) => Math.max(mx, s.sortOrder), -1)
      const suite: DemoTestSuite = { id: db.nextId(), projectId, name: String(body?.name ?? ''), sortOrder: maxOrder + 1 }
      db.state.testSuites.push(suite)
      db.save()
      return suite
    },
  },
  {
    method: 'PATCH',
    pattern: /^\/api\/test-suites\/(\d+)$/,
    handler: ({ params, body, db }) => {
      requireUser(db)
      const suite = db.state.testSuites.find((s) => s.id === Number(params[0]))
      if (!suite) throw TESTING_NOT_FOUND()
      if (typeof body?.name === 'string') suite.name = body.name
      if (typeof body?.sortOrder === 'number') suite.sortOrder = body.sortOrder
      db.save()
      return suite
    },
  },
  {
    method: 'DELETE',
    pattern: /^\/api\/test-suites\/(\d+)$/,
    handler: ({ params, db }) => {
      requireUser(db)
      const id = Number(params[0])
      // 소속 케이스는 미분류로 이동.
      for (const t of db.state.testCases) {
        if (t.suiteId === id) t.suiteId = null
      }
      db.state.testSuites = db.state.testSuites.filter((s) => s.id !== id)
      db.save()
      return null
    },
  },

  /* ── 테스트 케이스 관리: 케이스 (bulk 가 더 구체적이므로 먼저) ── */
  {
    method: 'POST',
    pattern: /^\/api\/projects\/(\d+)\/test-cases\/bulk$/,
    handler: ({ params, body, db }) => {
      requireUser(db)
      const projectId = Number(params[0])
      const suiteId: number | null = body?.suiteId ?? null
      const flowId: number | null = body?.flowId ?? null
      const reqs = (Array.isArray(body?.cases) ? body.cases : []) as TestCaseCreateRequest[]
      const now = db.nowIso()
      const created = reqs.map((req) => {
        const t: DemoTestCase = {
          id: db.nextId(),
          projectId,
          suiteId: req.suiteId ?? suiteId,
          title: req.title,
          precondition: req.precondition ?? null,
          steps: req.steps ?? [],
          priority: (lower(req.priority) || 'medium') as QaPriority,
          // 플로우에서 뽑아낸 케이스는 FLOW 오리진으로 연결.
          origin: flowId != null ? 'FLOW' : 'MANUAL',
          flowId,
          flowStale: false,
          createdAt: now,
          updatedAt: now,
        }
        db.state.testCases.push(t)
        return t
      })
      db.save()
      return created
    },
  },
  {
    method: 'GET',
    pattern: /^\/api\/projects\/(\d+)\/test-cases$/,
    handler: ({ params, db }) => {
      requireUser(db)
      return db.state.testCases.filter((t) => t.projectId === Number(params[0]))
    },
  },
  {
    method: 'POST',
    pattern: /^\/api\/projects\/(\d+)\/test-cases$/,
    handler: ({ params, body, db }) => {
      requireUser(db)
      const req = body as TestCaseCreateRequest
      const now = db.nowIso()
      const t: DemoTestCase = {
        id: db.nextId(),
        projectId: Number(params[0]),
        suiteId: req.suiteId ?? null,
        title: req.title,
        precondition: req.precondition ?? null,
        steps: req.steps ?? [],
        priority: (lower(req.priority) || 'medium') as QaPriority,
        origin: 'MANUAL',
        flowId: null,
        flowStale: false,
        createdAt: now,
        updatedAt: now,
      }
      db.state.testCases.push(t)
      db.save()
      return t
    },
  },
  {
    method: 'PATCH',
    pattern: /^\/api\/test-cases\/(\d+)$/,
    handler: ({ params, body, db }) => {
      requireUser(db)
      const t = db.state.testCases.find((x) => x.id === Number(params[0]))
      if (!t) throw TESTING_NOT_FOUND()
      const req = body as TestCaseUpdateRequest
      // suiteId 0 은 미분류(스위트 해제).
      if (req.suiteId !== undefined) t.suiteId = req.suiteId === 0 ? null : req.suiteId
      if (req.title !== undefined) t.title = req.title
      if (req.precondition !== undefined) t.precondition = req.precondition || null
      if (req.steps !== undefined) t.steps = req.steps
      if (req.priority !== undefined) t.priority = lower(req.priority) as QaPriority
      // 제목/스텝을 직접 수정하면 플로우 변경분을 반영한 것으로 보고 stale 해제.
      if (req.title !== undefined || req.steps !== undefined) t.flowStale = false
      t.updatedAt = db.nowIso()
      db.save()
      return t
    },
  },
  {
    method: 'DELETE',
    pattern: /^\/api\/test-cases\/(\d+)$/,
    handler: ({ params, db }) => {
      requireUser(db)
      const id = Number(params[0])
      db.state.testCases = db.state.testCases.filter((t) => t.id !== id)
      // 런 스냅샷은 유지하고 원본 참조만 끊는다.
      for (const rc of db.state.testRunCases) {
        if (rc.caseId === id) rc.caseId = null
      }
      db.save()
      return null
    },
  },

  /* ── 테스트 케이스 관리: 플로우 ── */
  {
    method: 'GET',
    pattern: /^\/api\/projects\/(\d+)\/test-flows$/,
    handler: ({ params, db }) => {
      requireUser(db)
      return db.state.testFlows
        .filter((f) => f.projectId === Number(params[0]))
        .map((f) => db.flowSummaryDto(f))
    },
  },
  {
    method: 'POST',
    pattern: /^\/api\/projects\/(\d+)\/test-flows$/,
    handler: ({ params, body, db }) => {
      requireUser(db)
      const f: DemoTestFlow = {
        id: db.nextId(),
        projectId: Number(params[0]),
        updateId: body?.updateId ?? null,
        name: String(body?.name ?? ''),
        graph: { nodes: [], edges: [] },
        updatedAt: db.nowIso(),
      }
      db.state.testFlows.push(f)
      db.save()
      return f
    },
  },
  {
    method: 'GET',
    pattern: /^\/api\/test-flows\/(\d+)$/,
    handler: ({ params, db }) => {
      requireUser(db)
      const f = db.state.testFlows.find((x) => x.id === Number(params[0]))
      if (!f) throw TESTING_NOT_FOUND()
      return f
    },
  },
  {
    method: 'PATCH',
    pattern: /^\/api\/test-flows\/(\d+)$/,
    handler: ({ params, body, db }) => {
      requireUser(db)
      const f = db.state.testFlows.find((x) => x.id === Number(params[0]))
      if (!f) throw TESTING_NOT_FOUND()
      if (typeof body?.name === 'string') f.name = body.name
      // updateId 0 은 업데이트 연결 해제.
      if (body?.updateId !== undefined) f.updateId = body.updateId === 0 ? null : body.updateId
      if (body?.graph !== undefined) {
        const next = body.graph as FlowGraph
        // 그래프가 실제로 바뀐 경우에만 이 플로우에서 만든 케이스를 stale 처리.
        if (JSON.stringify(f.graph) !== JSON.stringify(next)) {
          for (const t of db.state.testCases) {
            if (t.flowId === f.id) t.flowStale = true
          }
        }
        f.graph = next
      }
      f.updatedAt = db.nowIso()
      db.save()
      return f
    },
  },
  {
    method: 'DELETE',
    pattern: /^\/api\/test-flows\/(\d+)$/,
    handler: ({ params, db }) => {
      requireUser(db)
      const id = Number(params[0])
      db.state.testFlows = db.state.testFlows.filter((f) => f.id !== id)
      // 케이스는 남기고 플로우 연결만 해제.
      for (const t of db.state.testCases) {
        if (t.flowId === id) {
          t.flowId = null
          t.flowStale = false
        }
      }
      db.save()
      return null
    },
  },

  /* ── 테스트 케이스 관리: 런 ── */
  {
    method: 'GET',
    pattern: /^\/api\/updates\/(\d+)\/test-runs$/,
    handler: ({ params, db }) => {
      requireUser(db)
      return db.state.testRuns
        .filter((r) => r.updateId === Number(params[0]))
        .map((r) => db.runDto(r))
    },
  },
  {
    method: 'POST',
    pattern: /^\/api\/updates\/(\d+)\/test-runs$/,
    handler: ({ params, body, db }) => {
      requireUser(db)
      const updateId = Number(params[0])
      const upd = db.state.updates.find((u) => u.id === updateId)
      if (!upd) throw apiError(404, 'NOT_FOUND', tr('demo.api.updateNotFound', '업데이트를 찾을 수 없습니다.'))
      const caseIds: number[] = Array.isArray(body?.caseIds) ? body.caseIds : []
      // 플랫폼 다중 선택 시 케이스 × 플랫폼으로 실행 항목 확장. 비어 있으면 공통 1회(null).
      const validPlatforms = ['PC', 'ANDROID', 'IOS']
      const platforms: (string | null)[] = Array.isArray(body?.platforms) && body.platforms.length > 0
        ? body.platforms.filter((p: string) => validPlatforms.includes(p))
        : [null]
      if (platforms.length === 0) platforms.push(null)
      const run: DemoTestRun = {
        id: db.nextId(),
        updateId,
        name: String(body?.name ?? ''),
        closedAt: null,
        createdAt: db.nowIso(),
      }
      db.state.testRuns.unshift(run)
      // 선택한 케이스를 현재 내용 그대로 스냅샷 (이후 원본 수정과 분리).
      let order = 0
      for (const caseId of caseIds) {
        const t = db.state.testCases.find((x) => x.id === caseId)
        if (!t) continue
        for (const platform of platforms) {
          const rc: DemoTestRunCase = {
            id: db.nextId(),
            runId: run.id,
            caseId: t.id,
            platform: platform as DemoTestRunCase['platform'],
            sortOrder: order++,
            title: t.title,
            precondition: t.precondition,
            steps: JSON.parse(JSON.stringify(t.steps)),
            priority: t.priority,
            result: 'PENDING',
            note: null,
            qaItemId: null,
            executedAt: null,
          }
          db.state.testRunCases.push(rc)
        }
      }
      db.save()
      return db.runDetailDto(run)
    },
  },
  {
    method: 'GET',
    pattern: /^\/api\/projects\/(\d+)\/test-runs$/,
    handler: ({ params, db }) => {
      requireUser(db)
      const updIds = db.state.updates.filter((u) => u.projectId === Number(params[0])).map((u) => u.id)
      return db.state.testRuns
        .filter((r) => updIds.includes(r.updateId))
        .map((r) => db.runDto(r))
    },
  },
  {
    method: 'GET',
    pattern: /^\/api\/test-runs\/(\d+)$/,
    handler: ({ params, db }) => {
      requireUser(db)
      const run = db.state.testRuns.find((r) => r.id === Number(params[0]))
      if (!run) throw TESTING_NOT_FOUND()
      return db.runDetailDto(run)
    },
  },
  {
    method: 'PATCH',
    pattern: /^\/api\/test-runs\/(\d+)$/,
    handler: ({ params, body, db }) => {
      requireUser(db)
      const run = db.state.testRuns.find((r) => r.id === Number(params[0]))
      if (!run) throw TESTING_NOT_FOUND()
      // { closed } 로 마감/재개 토글.
      if (body?.closed !== undefined) run.closedAt = body.closed ? db.nowIso() : null
      db.save()
      return db.runDto(run)
    },
  },
  {
    method: 'DELETE',
    pattern: /^\/api\/test-runs\/(\d+)$/,
    handler: ({ params, db }) => {
      requireUser(db)
      const id = Number(params[0])
      db.state.testRunCases = db.state.testRunCases.filter((c) => c.runId !== id)
      db.state.testRuns = db.state.testRuns.filter((r) => r.id !== id)
      db.save()
      return null
    },
  },
  {
    method: 'PATCH',
    pattern: /^\/api\/test-run-cases\/(\d+)$/,
    handler: ({ params, body, db }) => {
      requireUser(db)
      const rc = db.state.testRunCases.find((x) => x.id === Number(params[0]))
      if (!rc) throw TESTING_NOT_FOUND()
      if (body?.result !== undefined) {
        rc.result = body.result as TestRunCaseResult
        // PENDING 으로 되돌리면 실행 이력도 초기화.
        rc.executedAt = rc.result === 'PENDING' ? null : db.nowIso()
      }
      if (body?.note !== undefined) rc.note = body.note === '' ? null : body.note
      if (body?.qaItemId !== undefined) rc.qaItemId = body.qaItemId === 0 ? null : body.qaItemId
      db.save()
      return rc
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
      if (!q) throw apiError(404, 'NOT_FOUND', tr('demo.api.qaNotFound', 'QA 항목을 찾을 수 없습니다.'))
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
      if (!n) throw apiError(404, 'NOT_FOUND', tr('demo.api.notificationNotFound', '알림을 찾을 수 없습니다.'))
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
    return Promise.reject(apiError(404, 'NOT_FOUND', tr('demo.api.notImplemented', `데모 mock 미구현 경로: ${method} ${path}`, { method, path })))
  }
  return impl
}
