import type {
  Member,
  MemberCreateRequest,
  MemberUpdateRequest,
  TeamsTestResult,
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
    updateEmail: (id: number, email: string | null) =>
      api<Member>(`/api/members/${id}/email`, { method: 'PUT', body: { email } }),
    updateTeamsNotify: (id: number, enabled: boolean) =>
      api<Member>(`/api/members/${id}/teams-notify`, { method: 'PUT', body: { enabled } }),
    teamsTest: (id: number) =>
      api<TeamsTestResult>(`/api/members/${id}/teams-test`, { method: 'POST' }),
  }
}
