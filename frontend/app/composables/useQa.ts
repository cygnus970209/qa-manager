import type {
  QaItem,
  QaPage,
  QaDashboardStats,
  QaCreateRequest,
  QaPatchRequest,
  QaHistoryEntry,
  QaComment,
  CommentCreateRequest,
  CommentUpdateRequest,
} from '~/types/api'

export function useQa() {
  const api = useApi()
  return {
    list: (params?: {
      updateId?: number
      status?: string
      priority?: string
      assigneeId?: number
      testerId?: number
    }) =>
      api<QaItem[]>('/api/qa', { query: params }),
    /** 서버 페이징 목록. size 는 10/50/100 만 허용. */
    page: (params?: {
      page?: number
      size?: number
      updateId?: number
      status?: string
      priority?: string
      assigneeId?: number
      testerId?: number
    }) =>
      api<QaPage>('/api/qa/page', { query: params }),
    /** 대시보드 수치 집계. mine=true 면 내가 테스터/담당자인 QA 만. */
    dashboardStats: (mine = false) =>
      api<QaDashboardStats>('/api/qa/dashboard-stats', { query: { mine } }),
    get: (id: number) =>
      api<QaItem>(`/api/qa/${id}`),
    create: (body: QaCreateRequest) =>
      api<QaItem>('/api/qa', { method: 'POST', body }),
    update: (id: number, body: QaPatchRequest) =>
      api<QaItem>(`/api/qa/${id}`, { method: 'PATCH', body }),
    remove: (id: number) =>
      api(`/api/qa/${id}`, { method: 'DELETE' }),
    history: (id: number) =>
      api<QaHistoryEntry[]>(`/api/qa/${id}/history`),
    listComments: (qaId: number) =>
      api<QaComment[]>(`/api/qa/${qaId}/comments`),
    createComment: (qaId: number, body: CommentCreateRequest) =>
      api<QaComment>(`/api/qa/${qaId}/comments`, { method: 'POST', body }),
    updateComment: (id: number, body: CommentUpdateRequest) =>
      api<QaComment>(`/api/comments/${id}`, { method: 'PATCH', body }),
    removeComment: (id: number) =>
      api(`/api/comments/${id}`, { method: 'DELETE' }),
    toggleReaction: (commentId: number, emoji: string) =>
      api<QaComment>(`/api/comments/${commentId}/reactions`, {
        method: 'POST',
        body: { emoji },
      }),
  }
}
