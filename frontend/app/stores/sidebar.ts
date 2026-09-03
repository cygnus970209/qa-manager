import { defineStore } from 'pinia'
import type { Project, QaProjectSummary } from '~/types/api'

/** 접힘 상태 localStorage 키 (사용자별이 아니라 브라우저별로 기억) */
const COLLAPSED_KEY = 'qam-sidebar-collapsed'

/**
 * 앱 사이드바 상태.
 * - 프로젝트 목록 + 프로젝트별 QA 집계(사이드바 배지)는 여기서 한 번 로드하고, 프로젝트를 만들거나
 *   고정/상태를 바꾼 페이지가 reload() 로 갱신한다.
 * - 접힘: 사용자 선호(localStorage) 위에 페이지가 잠시 강제하는 값(QA 상세 = 자동 접힘)을 얹는다.
 *   강제 중에 사용자가 펼치면 그 페이지에서만 펼쳐지고, 페이지를 나가면 선호값으로 돌아간다.
 */
export const useSidebarStore = defineStore('sidebar', () => {
  const projects = ref<Project[]>([])
  const stats = ref<Map<number, QaProjectSummary>>(new Map())
  const loaded = ref(false)
  let loadPromise: Promise<void> | null = null

  /** 사용자가 고른 접힘 상태 */
  const collapsedPref = ref(false)
  /** 페이지가 강제한 접힘 상태 (null = 강제 없음) */
  const forced = ref<boolean | null>(null)
  const collapsed = computed(() => forced.value ?? collapsedPref.value)

  /** 모바일(드로어) 열림 */
  const mobileOpen = ref(false)

  /** 현재 화면이 속한 프로젝트 — 사이드바 트리에서 펼쳐 보여줄 항목. 페이지가 설정/해제한다 */
  const activeProjectId = ref<number | null>(null)

  function initPref() {
    try {
      collapsedPref.value = localStorage.getItem(COLLAPSED_KEY) === '1'
    } catch { /* localStorage 접근 불가(프라이빗 모드 등) */ }
  }

  function setCollapsed(v: boolean) {
    if (forced.value !== null) {
      forced.value = v
      return
    }
    collapsedPref.value = v
    try { localStorage.setItem(COLLAPSED_KEY, v ? '1' : '0') } catch { /* ignore */ }
  }

  function toggle() {
    setCollapsed(!collapsed.value)
  }

  /** 페이지 단위 강제 접힘. 나갈 때 null 로 되돌릴 것 */
  function force(v: boolean | null) {
    forced.value = v
  }

  async function load() {
    const projectsApi = useProjects()
    const qaApi = useQa()
    const p = (async () => {
      const [list, s] = await Promise.all([projectsApi.list(), qaApi.dashboardStats()])
      projects.value = list
      const m = new Map<number, QaProjectSummary>()
      for (const row of s.byProject) m.set(row.projectId, row)
      stats.value = m
      loaded.value = true
    })()
    loadPromise = p
    try {
      await p
    } finally {
      if (loadPromise === p) loadPromise = null
    }
  }

  /** 최초 1회만 로드. 진행 중이면 그 완료를 기다린다 */
  async function ensureLoaded() {
    if (loaded.value) return
    if (loadPromise) await loadPromise
    else await load()
  }

  /** 프로젝트 생성/삭제/고정/상태 변경 후 호출 */
  async function reload() {
    try { await load() } catch { /* 사이드바 갱신 실패는 조용히 무시 — 다음 로드에서 복구 */ }
  }

  function reset() {
    projects.value = []
    stats.value = new Map()
    loaded.value = false
    activeProjectId.value = null
  }

  return {
    projects, stats, loaded,
    collapsed, mobileOpen, activeProjectId,
    initPref, setCollapsed, toggle, force,
    load, ensureLoaded, reload, reset,
  }
})
