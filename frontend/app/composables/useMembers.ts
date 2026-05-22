import type {
  Member,
  MemberCreateRequest,
  MemberUpdateRequest,
} from '~/types/api'

export function useMembers() {
  const api = useApi()
  return {
    list: () => api<Member[]>('/api/members'),
    get: (id: number) => api<Member>(`/api/members/${id}`),
    create: (body: MemberCreateRequest) =>
      api<Member>('/api/members', { method: 'POST', body }),
    update: (id: number, body: MemberUpdateRequest) =>
      api<Member>(`/api/members/${id}`, { method: 'PATCH', body }),
    remove: (id: number) =>
      api(`/api/members/${id}`, { method: 'DELETE' }),
  }
}
