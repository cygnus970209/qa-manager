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
    /** 사이드바 프로젝트 순서 저장 (사용자별). 저장 후 정렬된 목록을 돌려준다. */
    reorder: (projectIds: number[]) =>
      api<Project[]>('/api/projects/order', { method: 'PUT', body: { projectIds } }),
  }
}
