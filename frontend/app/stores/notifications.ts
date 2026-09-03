import { defineStore } from 'pinia'
import type { Notification } from '~/types/api'

/**
 * 알림 스토어.
 * - load()로 초기 로드
 * - connect()로 SSE 구독 (HttpOnly 쿠키로 자동 인증)
 * - disconnect()로 정리
 *
 * SSE 는 fetch + ReadableStream 으로 구현하여 credentials 쿠키 자동 첨부.
 * (EventSource 의 헤더 미지원 한계와 무관)
 *
 * 재연결:
 * - 서버 SseEmitter 는 30분 타임아웃으로 스트림을 닫고, 프록시/네트워크 단절도 있으므로
 *   스트림이 끝나면 지수 백오프(1s → 최대 30s)로 자동 재연결한다.
 * - 재연결에 성공하면 끊긴 동안 놓친 알림을 목록 재조회로 동기화한다.
 * - 서버 keep-alive 코멘트(25s 간격)가 STALE_MS 동안 없으면 죽은 연결로 보고 끊고 재연결한다.
 * - 인증 만료(401/403)면 중단한다. 로그인되면 AppNavbar 가 connect() 를 다시 호출한다.
 * 데스크톱 앱은 창을 닫아도 트레이에 남아 이 스토어가 계속 살아 있으므로, 재연결이 곧 알림 수신 보장이다.
 */
/**
 * 데스크톱 앱(별도 리포: qa-manager-desktop) 브리지.
 * 데스크톱 셸이 웹뷰에 주입하는 선택적 전역 객체 — 없으면 아무 동작도 하지 않는다.
 * Tauri 등 특정 기술에 종속되지 않는 인터페이스로 유지할 것.
 */
interface DesktopBridge {
  /** 네이티브 알림. tag 를 주면 클릭 시 onNotificationClick(tag) 로 되돌아온다 */
  notify?: (p: { title: string; body: string; tag?: string }) => void
  setBadge?: (count: number) => void
  /** 데스크톱 셸이 네이티브 알림 클릭 시 호출한다. 웹앱이 등록 */
  onNotificationClick?: ((tag: string) => void) | null
}
function desktopBridge(): DesktopBridge | undefined {
  return import.meta.client ? (window as any).__QAM_DESKTOP__ : undefined
}

const RECONNECT_BASE_MS = 1_000
const RECONNECT_MAX_MS = 30_000
/** 이 시간 이상 연결이 유지됐으면 정상 연결로 보고 백오프를 초기화한다 */
const HEALTHY_AFTER_MS = 10_000
/** 서버 keep-alive(25s) 3회 이상 누락 → 죽은 연결로 간주 */
const STALE_MS = 90_000

export const useNotificationsStore = defineStore('notifications', () => {
  const router = useRouter()
  const appUpdate = useAppUpdate()
  const items = ref<Notification[]>([])
  const unreadCount = computed(() => items.value.filter((n) => !n.read).length)

  // 데스크톱 앱: 안읽음 수 → 독/작업표시줄 뱃지
  if (import.meta.client) {
    watch(unreadCount, (n) => {
      try { desktopBridge()?.setBadge?.(n) } catch { /* 브리지 없음 */ }
    })
  }

  let abort: AbortController | null = null
  let reconnectTimer: ReturnType<typeof setTimeout> | null = null
  let staleTimer: ReturnType<typeof setTimeout> | null = null
  /** 연속 실패 횟수 (백오프 계산용) */
  let attempt = 0
  /** connect() ~ disconnect() 사이 true. 스트림이 끝났을 때 재연결할지 결정한다 */
  let wantConnected = false
  /** 직전 연결이 열렸다가 끊겼으면 true → 다음 연결 성공 시 목록 재조회 */
  let needResync = false

  /** 진행 중이거나 완료된 최초 로드. 페이지가 알림 목록에 의존할 때 ensureLoaded() 로 기다린다 */
  let loadPromise: Promise<void> | null = null

  async function load() {
    const api = useApi()
    const p = api<Notification[]>('/api/notifications').then((list) => { items.value = list })
    loadPromise = p
    await p
  }

  /** 아직 한 번도 로드하지 않았으면 로드하고, 진행 중이면 그 완료를 기다린다 */
  async function ensureLoaded() {
    if (!loadPromise) await load()
    else await loadPromise
  }

  async function markRead(id: number) {
    const api = useApi()
    const updated = await api<Notification>(`/api/notifications/${id}/read`, { method: 'PATCH' })
    items.value = items.value.map((n) => n.id === id ? updated : n)
  }

  async function markAllRead() {
    const api = useApi()
    await api('/api/notifications/read-all', { method: 'PATCH' })
    items.value = items.value.map((n) => (n.read ? n : { ...n, read: true }))
  }

  /**
   * 특정 QA 에 대한 안읽은 알림을 모두 읽음 처리. QA 상세 페이지에 들어왔을 때 호출한다.
   * 알림을 클릭해 들어왔든 목록에서 직접 들어왔든, 그 QA 를 봤으면 관련 알림은 읽은 것으로 본다.
   */
  async function markReadForQa(qaItemId: number) {
    try { await ensureLoaded() } catch { return }
    const targets = items.value.filter((n) => n.qaItemId === qaItemId && !n.read)
    await Promise.all(targets.map((n) => markRead(n.id).catch(() => { /* 개별 실패는 무시 */ })))
  }

  /** 알림 열기: 안읽음이면 읽음 처리하고 연결된 QA(없으면 프로젝트)로 이동. 알림센터 클릭과 데스크톱 알림 클릭이 함께 쓴다 */
  async function openNotification(id: number) {
    const n = items.value.find((x) => x.id === id)
    if (!n) return
    if (!n.read) {
      try { await markRead(id) } catch { /* 읽음 처리 실패해도 이동은 진행 */ }
    }
    if (n.qaItemId) await router.push(`/qa/${n.qaItemId}`)
    else if (n.projectId) await router.push(`/project/${n.projectId}`)
  }

  // 데스크톱 앱: 네이티브 알림 클릭 → 해당 알림 열기 (tag = 알림 id)
  if (import.meta.client) {
    const bridge = desktopBridge()
    if (bridge) {
      bridge.onNotificationClick = (tag) => {
        const id = Number(tag)
        if (Number.isFinite(id)) void openNotification(id)
      }
    }
  }

  /** 중복(재조회와 SSE 가 겹치는 경우) 없이 맨 앞에 추가. 실제로 추가됐으면 true */
  function prepend(n: Notification): boolean {
    if (items.value.some((x) => x.id === n.id)) return false
    items.value = [n, ...items.value]
    return true
  }

  function connect() {
    if (!import.meta.client) return
    const config = useRuntimeConfig()
    if (config.public.demoMode === true) return // 데모 모드: 실시간(SSE) 없음
    wantConnected = true
    if (abort || reconnectTimer) return // 이미 연결 중이거나 재연결 대기 중
    void runStream()
  }

  async function runStream() {
    const config = useRuntimeConfig()
    const controller = new AbortController()
    abort = controller
    let openedAt: number | null = null
    try {
      const res = await fetch(`${config.public.apiBase}/api/notifications/stream`, {
        method: 'GET',
        credentials: 'include',
        headers: {
          Accept: 'text/event-stream',
        },
        signal: controller.signal,
      })
      if (res.status === 401 || res.status === 403) {
        // 인증 만료: 재연결해도 소용없다. 로그인되면 connect() 가 다시 호출된다.
        wantConnected = false
        return
      }
      if (!res.ok || !res.body) {
        // 원인 진단용: 응답 status / content-type / body 출력
        let body = ''
        try { body = await res.text() } catch { /* ignore */ }
        console.error('[SSE] connect failed',
          'status=', res.status,
          'ctype=', res.headers.get('content-type'),
          'body=', body)
        return
      }
      openedAt = Date.now()
      if (needResync) {
        // 끊긴 동안 놓친 알림 동기화 (SSE 로 겹쳐 들어오는 건 prepend 가 걸러낸다)
        needResync = false
        try { await load() } catch { /* 다음 재연결 때 다시 시도 */ }
        // 서버가 재시작됐다면 새 빌드가 배포됐을 가능성이 크다 — 바로 확인해 배너를 띄운다
        void appUpdate.check()
      }
      const reader = res.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      touchStale(controller)
      while (true) {
        const { value, done } = await reader.read()
        if (done) break
        touchStale(controller)
        buffer += decoder.decode(value, { stream: true })
        const events = buffer.split('\n\n')
        buffer = events.pop() ?? ''
        for (const block of events) {
          processEvent(block)
        }
      }
    } catch {
      // 네트워크 오류 / abort
    } finally {
      clearStale()
      if (abort === controller) abort = null
      if (openedAt !== null) {
        needResync = true
        if (Date.now() - openedAt >= HEALTHY_AFTER_MS) attempt = 0
      }
      // disconnect() 는 wantConnected 를 먼저 내리므로 수동 종료는 재연결하지 않는다.
      // abort 가 남아 있으면 이미 다른 스트림이 도는 중(disconnect→connect 연속 호출)이므로 건너뛴다.
      if (wantConnected && !abort) scheduleReconnect()
    }
  }

  function scheduleReconnect() {
    if (reconnectTimer) return
    const base = Math.min(RECONNECT_BASE_MS * 2 ** attempt, RECONNECT_MAX_MS)
    const delay = base + Math.random() * base * 0.3 // 지터: 동시 재연결 분산
    attempt = Math.min(attempt + 1, 10)
    reconnectTimer = setTimeout(() => {
      reconnectTimer = null
      if (wantConnected && !abort) void runStream()
    }, delay)
  }

  function touchStale(controller: AbortController) {
    clearStale()
    staleTimer = setTimeout(() => {
      staleTimer = null
      // keep-alive 가 끊겼다 → 죽은 연결. wantConnected 는 유지되므로 finally 에서 재연결된다.
      if (abort === controller) controller.abort()
    }, STALE_MS)
  }

  function clearStale() {
    if (staleTimer) {
      clearTimeout(staleTimer)
      staleTimer = null
    }
  }

  function processEvent(block: string) {
    let event = 'message'
    const dataLines: string[] = []
    for (const line of block.split('\n')) {
      if (line.startsWith('event:')) event = line.slice(6).trim()
      else if (line.startsWith('data:')) dataLines.push(line.slice(5).trim())
      // ':keep-alive' 같은 코멘트 라인은 무시 (연결 유지 확인은 touchStale 이 담당)
    }
    if (dataLines.length === 0) return
    const raw = dataLines.join('\n')
    if (event === 'notification') {
      try {
        const n = JSON.parse(raw) as Notification
        if (!prepend(n)) return
        // 데스크톱 앱: 네이티브 OS 알림 — 제목 = QA 제목, 본문 = 문구 + 댓글 발췌, tag = 알림 id (클릭 시 이동용)
        try { desktopBridge()?.notify?.({ title: n.title || 'QA Manager', body: n.message, tag: String(n.id) }) } catch { /* 브리지 없음 */ }
      } catch { /* ignore */ }
    }
  }

  function disconnect() {
    wantConnected = false
    needResync = false
    attempt = 0
    if (reconnectTimer) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
    clearStale()
    abort?.abort()
    abort = null
  }

  // 네트워크 복구 시 백오프를 기다리지 않고 즉시 재연결
  if (import.meta.client) {
    window.addEventListener('online', () => {
      if (!wantConnected || abort) return
      if (reconnectTimer) {
        clearTimeout(reconnectTimer)
        reconnectTimer = null
      }
      attempt = 0
      void runStream()
    })
  }

  return { items, unreadCount, load, ensureLoaded, markRead, markAllRead, markReadForQa, openNotification, connect, disconnect }
})
