import type {
  Project,
  ProjectCreateRequest,
  ProjectUpdateRequest,
} from '~/types/api'

export function useProjects() {
  const api = useApi()
  return {
    list: () => api<Project[]>('/api/projects'),
    get: (id: number) => api<Project>(`/api/projects/${id}`),
    create: (body: ProjectCreateRequest) =>
      api<Project>('/api/projects', { method: 'POST', body }),
    update: (id: number, body: ProjectUpdateRequest) =>
      api<Project>(`/api/projects/${id}`, { method: 'PATCH', body }),
    remove: (id: number) =>
      api(`/api/projects/${id}`, { method: 'DELETE' }),
    togglePin: (id: number) =>
      api<{ pinned: boolean }>(`/api/projects/${id}/pin`, { method: 'POST' }),
  }
}
