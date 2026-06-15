import { defineStore } from 'pinia'
import type { Notification } from '~/types/api'

/**
 * 알림 스토어.
 * - list()로 초기 로드
 * - connect()로 SSE 구독 (HttpOnly 쿠키로 자동 인증)
 * - disconnect()로 정리
 *
 * SSE 는 fetch + ReadableStream 으로 구현하여 credentials 쿠키 자동 첨부.
 * (EventSource 의 헤더 미지원 한계와 무관)
 */
export const useNotificationsStore = defineStore('notifications', () => {
  const items = ref<Notification[]>([])
  const unreadCount = computed(() => items.value.filter((n) => !n.read).length)

  let abort: AbortController | null = null

  async function load() {
    const api = useApi()
    items.value = await api<Notification[]>('/api/notifications')
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

  async function connect() {
    if (!import.meta.client) return
    if (abort) return // 이미 연결됨
    const config = useRuntimeConfig()
    if (config.public.demoMode === true) return // 데모 모드: 실시간(SSE) 없음

    abort = new AbortController()
    try {
      const res = await fetch(`${config.public.apiBase}/api/notifications/stream`, {
        method: 'GET',
        credentials: 'include',
        headers: {
          Accept: 'text/event-stream',
        },
        signal: abort.signal,
      })
      if (!res.ok || !res.body) {
        // 원인 진단용: 응답 status / content-type / body 출력
        let body = ''
        try { body = await res.text() } catch { /* ignore */ }
        console.error('[SSE] connect failed',
          'status=', res.status,
          'ctype=', res.headers.get('content-type'),
          'body=', body)
        abort = null
        return
      }
      const reader = res.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      while (true) {
        const { value, done } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })
        const events = buffer.split('\n\n')
        buffer = events.pop() ?? ''
        for (const block of events) {
          processEvent(block)
        }
      }
    } catch {
      // 네트워크 / abort
    } finally {
      abort = null
    }
  }

  function processEvent(block: string) {
    let event = 'message'
    const dataLines: string[] = []
    for (const line of block.split('\n')) {
      if (line.startsWith('event:')) event = line.slice(6).trim()
      else if (line.startsWith('data:')) dataLines.push(line.slice(5).trim())
    }
    if (dataLines.length === 0) return
    const raw = dataLines.join('\n')
    if (event === 'notification') {
      try {
        const n = JSON.parse(raw) as Notification
        items.value = [n, ...items.value]
      } catch { /* ignore */ }
    }
  }

  function disconnect() {
    abort?.abort()
    abort = null
  }

  return { items, unreadCount, load, markRead, markAllRead, connect, disconnect }
})
