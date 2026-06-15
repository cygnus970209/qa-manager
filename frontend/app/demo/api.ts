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
function apiError(status: number, code: string, message: string) {
  const data = { timestamp: new Date().toISOString(), status, code, message }
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
      db.state.currentUserId = member.id
      db.save()
      return { authenticated: true, expiresInSeconds: 3600, user: db.meDto(member) }
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
        createdAt: now,
        updatedAt: now,
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

  /* ── Notifications (데모: 빈 목록) ── */
  { method: 'GET', pattern: /^\/api\/notifications$/, handler: () => [] },
  { method: 'PATCH', pattern: /^\/api\/notifications\/(\d+)\/read$/, handler: () => null },
  { method: 'PATCH', pattern: /^\/api\/notifications\/read-all$/, handler: () => null },

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
