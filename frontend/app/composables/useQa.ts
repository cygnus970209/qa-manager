import type {
  QaItem,
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
