import type { QaItem } from '~/types/api'

/** QA 목록 필터 상태. QAList(목록 페이지)와 상세 사이드바가 공유한다. */
export interface QaFilterState {
  status: string
  priority: string
  projectId: string
  updateId: string
  testerId: number | null
  assigneeId: number | null
  mineOnly: boolean
  search: string
}

export function emptyQaFilter(): QaFilterState {
  return {
    status: 'all',
    priority: 'all',
    projectId: 'all',
    updateId: 'all',
    testerId: null,
    assigneeId: null,
    mineOnly: false,
    search: '',
  }
}

/**
 * items 에 필터를 적용해 반환.
 * - meId: '내 것만' 판정용(로그인 사용자 id).
 * - updateToProject: 프로젝트 필터용 updateId→projectId 매핑. 없으면 프로젝트 필터는 무시한다.
 */
export function applyQaFilter(
  items: QaItem[],
  f: QaFilterState,
  meId?: number | null,
  updateToProject?: Map<number, number>,
): QaItem[] {
  return items.filter((item) => {
    if (f.status !== 'all' && item.status !== f.status) return false
    if (f.priority !== 'all' && item.priority !== f.priority) return false
    if (f.projectId !== 'all' && updateToProject) {
      if (String(updateToProject.get(item.updateId) ?? '') !== f.projectId) return false
    }
    if (f.updateId !== 'all' && String(item.updateId) !== f.updateId) return false

    if (f.testerId != null && item.tester?.id !== f.testerId) return false
    if (f.assigneeId != null
      && item.assignee1?.id !== f.assigneeId
      && item.assignee2?.id !== f.assigneeId) return false

    if (f.mineOnly && meId != null) {
      const matchMine = item.tester?.id === meId
        || item.assignee1?.id === meId
        || item.assignee2?.id === meId
      if (!matchMine) return false
    }

    if (f.search.trim()) {
      const s = f.search.toLowerCase()
      const names = [item.tester?.name, item.assignee1?.name, item.assignee2?.name]
        .filter((x): x is string => Boolean(x))
        .join(' ')
        .toLowerCase()
      return (
        item.title.toLowerCase().includes(s) ||
        (item.description ?? '').toLowerCase().includes(s) ||
        names.includes(s)
      )
    }
    return true
  })
}

const FILTER_KEY = 'qa:nav:filter'

/** 목록에서 상세로 진입할 때 현재 필터를 저장. 상세 사이드바가 이를 복원한다. */
export function saveQaFilter(f: QaFilterState) {
  if (!import.meta.client) return
  try {
    sessionStorage.setItem(FILTER_KEY, JSON.stringify(f))
  } catch { /* sessionStorage 접근 불가 환경은 무시 */ }
}

export function loadQaFilter(): QaFilterState | null {
  if (!import.meta.client) return null
  try {
    const raw = sessionStorage.getItem(FILTER_KEY)
    if (!raw) return null
    return { ...emptyQaFilter(), ...JSON.parse(raw) }
  } catch {
    return null
  }
}
