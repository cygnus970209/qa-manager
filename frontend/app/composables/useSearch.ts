import type { SearchCheck, SearchResponse, SearchStatus, SearchType } from '~/types/api'

export function useSearch() {
  const api = useApi()
  return {
    /** 통합 검색. types 를 비우면 전체, size 는 최대 50 */
    search: (params: { q: string; types?: SearchType[]; projectId?: number | null; page?: number; size?: number }) =>
      api<SearchResponse>('/api/search', {
        query: {
          q: params.q,
          types: params.types && params.types.length > 0 ? params.types.join(',') : undefined,
          projectId: params.projectId ?? undefined,
          page: params.page ?? 0,
          size: params.size ?? 20,
        },
      }),
    /** 인덱스 현황 (관리자) */
    status: () => api<SearchStatus>('/api/search/status'),
    /** 원본과 대조해 누락·고아·내용 변경을 찾는다 (관리자, 읽기 전용) */
    check: () => api<SearchCheck>('/api/search/check', { method: 'POST' }),
    /** 검사에서 나온 불일치만 고친다 → 다시 검사한 결과 (관리자) */
    repair: () => api<SearchCheck>('/api/search/repair', { method: 'POST' }),
    /** 인덱스 전체 재생성 (관리자) */
    reindex: () => api<SearchStatus>('/api/search/reindex', { method: 'POST' }),
  }
}

/** 검색 결과 항목이 가리키는 화면 */
export function searchItemPath(item: { type: SearchType; id: number; qaItemId: number | null; projectId: number | null; updateId: number | null }): string {
  switch (item.type) {
    case 'qa': return `/qa/${item.id}`
    case 'comment': return item.qaItemId ? `/qa/${item.qaItemId}#comment-${item.id}` : '/'
    case 'project': return `/project/${item.id}`
    case 'update': return item.projectId ? `/project/${item.projectId}?update=${item.id}` : '/'
    case 'test_case': return item.projectId ? `/project/${item.projectId}?tab=tests&case=${item.id}` : '/'
  }
}

/** 질의어 단어를 <mark> 로 감싼 HTML (텍스트는 이스케이프) */
export function highlight(text: string | null | undefined, query: string): string {
  const src = text ?? ''
  const esc = src.replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c] as string))
  const words = query.toLowerCase().split(/[^\p{L}\p{N}]+/u).filter((w) => w.length > 0)
  if (words.length === 0) return esc
  const pattern = new RegExp(`(${words.map((w) => w.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')).join('|')})`, 'giu')
  return esc.replace(pattern, '<mark class="rounded bg-amber-100 px-0.5 text-inherit dark:bg-amber-500/30">$1</mark>')
}
