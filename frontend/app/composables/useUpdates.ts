import type {
  ProjectUpdate,
  UpdateCreateRequest,
  UpdatePatchRequest,
} from '~/types/api'

export function useUpdates() {
  const api = useApi()
  return {
    listAll: () => api<ProjectUpdate[]>('/api/updates'),
    listByProject: (projectId: number) =>
      api<ProjectUpdate[]>(`/api/projects/${projectId}/updates`),
    get: (id: number) =>
      api<ProjectUpdate>(`/api/updates/${id}`),
    create: (projectId: number, body: UpdateCreateRequest) =>
      api<ProjectUpdate>(`/api/projects/${projectId}/updates`, { method: 'POST', body }),
    update: (id: number, body: UpdatePatchRequest) =>
      api<ProjectUpdate>(`/api/updates/${id}`, { method: 'PATCH', body }),
    remove: (id: number) =>
      api(`/api/updates/${id}`, { method: 'DELETE' }),
  }
}
