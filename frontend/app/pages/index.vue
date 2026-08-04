<script setup lang="ts">
import { FolderPlus, Plus, FileText, Wrench, Loader, Check, CheckCheck, Pause, RotateCcw, AlertTriangle } from '@lucide/vue'
import StatsCard from '~/components/feature/StatsCard.vue'
import ProjectCard from '~/components/feature/ProjectCard.vue'
import QAList from '~/components/feature/QAList.vue'
import NewProjectModal from '~/components/feature/NewProjectModal.vue'
import NewQAModal from '~/components/feature/NewQAModal.vue'
import TeamsSetupNotice from '~/components/feature/TeamsSetupNotice.vue'
import type { Member, Project, ProjectUpdate, QaPage, QaDashboardStats } from '~/types/api'

const projectsApi = useProjects()
const updatesApi = useUpdates()
const qaApi = useQa()
const membersApi = useMembers()
const auth = useAuthStore()
const router = useRouter()

const projects = ref<Project[]>([])
const updates = ref<ProjectUpdate[]>([])
const members = ref<Member[]>([])
const loading = ref(true)
const projectModalOpen = ref(false)
const qaModalOpen = ref(false)
const teamsNoticeOpen = ref(false)

// QA 목록은 서버 페이징으로 가져온다 (10/50/100).
const qaPage = ref<QaPage | null>(null)
const pageNum = ref(0)
const pageSize = ref(10)

// 대시보드 수치는 집계 API 사용. overall 은 프로젝트 카드에도 쓰이므로 항상 전체 기준.
const overallStats = ref<QaDashboardStats | null>(null)
const myStats = ref<QaDashboardStats | null>(null)
const myOnly = ref(false)

async function loadQaPage() {
  qaPage.value = await qaApi.page({ page: pageNum.value, size: pageSize.value })
}

async function load() {
  loading.value = true
  try {
    projects.value = await projectsApi.list()
    overallStats.value = await qaApi.dashboardStats()
    await loadQaPage()
    members.value = await membersApi.list()
    const lists = await Promise.all(projects.value.map((p) => updatesApi.listByProject(p.id)))
    updates.value = lists.flat()
    maybeShowTeamsNotice()
  } finally {
    loading.value = false
  }
}

/** Teams 봇 미설정자에게 안내 팝업을 하루 1회 노출 (localStorage 날짜 기록) */
function maybeShowTeamsNotice() {
  const uid = auth.user?.id
  if (!uid) return
  const me = members.value.find((m) => m.id === uid)
  if (!me || me.teamsLinked) return // 본인 정보 없음 또는 이미 봇 연동됨
  const key = `teams-setup-notice:${uid}`
  const today = new Date().toLocaleDateString('sv-SE') // 'YYYY-MM-DD' (로컬 기준)
  if (localStorage.getItem(key) === today) return // 오늘 이미 노출함
  localStorage.setItem(key, today)
  teamsNoticeOpen.value = true
}

function onTeamsNoticeConfirm() {
  teamsNoticeOpen.value = false
  router.push('/admin?tab=settings&sub=ms-teams')
}

if (import.meta.client) {
  onMounted(load)
}

const EMPTY_STATS: QaDashboardStats = {
  total: 0, needsFix: 0, inProgress: 0, fixDone: 0, confirmed: 0,
  onHold: 0, needsRecheck: 0, critical: 0, byProject: [],
}

// '내 작업만' 체크 시 내가 테스터/담당자로 지정된 QA 기준 집계로 전환한다.
const stats = computed(() => (myOnly.value ? myStats.value : overallStats.value) ?? EMPTY_STATS)

// 첫 체크 때만 내 작업 집계를 가져온다.
watch(myOnly, async (v) => {
  if (v && !myStats.value) myStats.value = await qaApi.dashboardStats(true)
})

// 프로젝트 카드 수치는 '내 작업만' 필터와 무관하게 전체 기준을 유지한다.
const projectQaStats = computed(() => {
  const stat = new Map<number, { count: number; resolved: number }>()
  for (const s of overallStats.value?.byProject ?? []) {
    stat.set(s.projectId, { count: s.count, resolved: s.resolved })
  }
  return stat
})

watch(pageSize, async () => {
  pageNum.value = 0
  await loadQaPage()
})

async function goPage(p: number) {
  pageNum.value = p
  await loadQaPage()
}

async function onTogglePin(id: number) {
  await projectsApi.togglePin(id)
  projects.value = await projectsApi.list()
}

function onProjectCreated(p: Project) {
  projects.value.unshift(p)
}
async function onQaCreated() {
  // 서버 페이징/집계 기준과 어긋나지 않게 재조회한다.
  myStats.value = null
  overallStats.value = await qaApi.dashboardStats()
  if (myOnly.value) myStats.value = await qaApi.dashboardStats(true)
  await loadQaPage()
}
</script>

<template>
  <section>
    <header class="mb-6 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
      <div>
        <h1 class="text-xl font-bold text-slate-800 md:text-2xl">QA 현황 대시보드</h1>
        <p class="mt-1 text-sm text-slate-500">프로젝트별 QA 진행 상황을 한눈에 확인하세요</p>
      </div>
      <div class="flex gap-2">
        <button
          type="button"
          class="inline-flex items-center gap-2 rounded-lg bg-emerald-500 px-4 py-2 text-sm font-medium text-white hover:bg-emerald-600"
          @click="projectModalOpen = true"
        >
          <FolderPlus class="h-4 w-4" />
          새 프로젝트
        </button>
        <button
          type="button"
          class="inline-flex items-center gap-2 rounded-lg bg-blue-500 px-4 py-2 text-sm font-medium text-white hover:bg-blue-600"
          @click="qaModalOpen = true"
        >
          <Plus class="h-4 w-4" />
          새 QA 항목
        </button>
      </div>
    </header>

    <section class="mb-6">
      <div class="mb-3 flex items-center justify-between">
        <h2 class="text-sm font-semibold text-slate-700">프로젝트</h2>
        <span class="text-xs text-slate-400">
          <template v-if="loading">불러오는 중…</template>
          <template v-else>총 {{ projects.length }}개 · 고정 {{ projects.filter(p => p.pinned).length }}개</template>
        </span>
      </div>
      <div v-if="loading" class="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4 md:gap-4">
        <div v-for="i in 4" :key="i" class="h-[152px] animate-pulse rounded-xl border border-slate-200 bg-white p-5">
          <div class="flex items-start justify-between">
            <div class="space-y-2">
              <div class="h-4 w-32 rounded bg-slate-200" />
              <div class="h-3 w-20 rounded bg-slate-100" />
            </div>
            <div class="h-5 w-5 rounded-full bg-slate-100" />
          </div>
          <div class="mt-6 space-y-2">
            <div class="h-2 w-full rounded-full bg-slate-100" />
            <div class="flex justify-between">
              <div class="h-3 w-12 rounded bg-slate-100" />
              <div class="h-3 w-10 rounded bg-slate-100" />
            </div>
          </div>
        </div>
      </div>
      <div v-else class="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4 md:gap-4">
        <ProjectCard
          v-for="p in projects"
          :key="p.id"
          :project="p"
          :qa-count="projectQaStats.get(p.id)?.count ?? 0"
          :resolved-count="projectQaStats.get(p.id)?.resolved ?? 0"
          @toggle-pin="onTogglePin"
        />
      </div>
    </section>

    <section class="mb-6">
      <div class="mb-3 flex items-center justify-between">
        <h2 class="text-sm font-semibold text-slate-700">QA 현황</h2>
        <label class="flex cursor-pointer select-none items-center gap-1.5 text-xs font-medium text-slate-500">
          <input
            v-model="myOnly"
            type="checkbox"
            class="h-3.5 w-3.5 rounded border-slate-300 text-blue-500 focus:ring-blue-400"
          />
          내 작업만
        </label>
      </div>
      <div class="grid grid-cols-2 gap-3 md:grid-cols-4 md:gap-4">
        <template v-if="loading">
          <div v-for="i in 8" :key="i" class="h-[96px] animate-pulse rounded-xl border border-slate-200 bg-white p-5">
            <div class="flex items-start justify-between">
              <div class="space-y-2">
                <div class="h-3 w-16 rounded bg-slate-200" />
                <div class="h-7 w-10 rounded bg-slate-200" />
                <div class="h-3 w-14 rounded bg-slate-100" />
              </div>
              <div class="h-10 w-10 rounded-lg bg-slate-100" />
            </div>
          </div>
        </template>
        <template v-else>
          <StatsCard title="전체 QA" :value="stats.total" :icon="FileText" icon-color="text-blue-500" icon-bg="bg-blue-50" />
          <StatsCard title="수정필요" :value="stats.needsFix" :icon="Wrench" icon-color="text-rose-500" icon-bg="bg-rose-50" />
          <StatsCard title="진행중" :value="stats.inProgress" :icon="Loader" icon-color="text-blue-500" icon-bg="bg-blue-50" />
          <StatsCard title="수정완료" :value="stats.fixDone" :icon="Check" icon-color="text-amber-500" icon-bg="bg-amber-50" />
          <StatsCard
            title="확인완료"
            :value="stats.confirmed"
            :icon="CheckCheck"
            icon-color="text-emerald-500"
            icon-bg="bg-emerald-50"
            :trend="`완료율 ${stats.total > 0 ? Math.round((stats.confirmed / stats.total) * 100) : 0}%`"
          />
          <StatsCard title="보류" :value="stats.onHold" :icon="Pause" icon-color="text-slate-500" icon-bg="bg-slate-100" />
          <StatsCard title="추가확인필요" :value="stats.needsRecheck" :icon="RotateCcw" icon-color="text-purple-500" icon-bg="bg-purple-50" />
          <StatsCard title="긴급" :value="stats.critical" :icon="AlertTriangle" icon-color="text-rose-500" icon-bg="bg-rose-50" />
        </template>
      </div>
    </section>

    <section>
      <h2 class="mb-3 text-sm font-semibold text-slate-700">전체 QA 항목</h2>
      <div v-if="loading" class="overflow-hidden rounded-xl border border-slate-200 bg-white">
        <div v-for="i in 5" :key="i" class="flex animate-pulse items-center gap-4 border-b border-slate-100 px-4 py-4 last:border-0">
          <div class="h-3 w-16 rounded bg-slate-100" />
          <div class="h-4 flex-1 rounded bg-slate-200" />
          <div class="h-3 w-20 rounded bg-slate-100" />
          <div class="h-5 w-16 rounded-full bg-slate-100" />
        </div>
      </div>
      <template v-else>
        <QAList :items="qaPage?.content ?? []" :updates="updates" :members="members" />
        <div class="mt-3 flex flex-wrap items-center justify-between gap-2">
          <div class="flex items-center gap-2 text-xs text-slate-500">
            <span>페이지당</span>
            <select
              v-model.number="pageSize"
              class="rounded-md border border-slate-200 bg-white px-2 py-1 text-xs focus:outline-none focus:ring-2 focus:ring-emerald-200"
            >
              <option :value="10">10개</option>
              <option :value="50">50개</option>
              <option :value="100">100개</option>
            </select>
            <span v-if="qaPage">총 {{ qaPage.totalElements.toLocaleString() }}건</span>
          </div>
          <div class="flex items-center gap-1">
            <button
              type="button"
              class="rounded-md border border-slate-200 px-3 py-1.5 text-xs font-medium text-slate-600 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40"
              :disabled="pageNum === 0"
              @click="goPage(pageNum - 1)"
            >
              이전
            </button>
            <span class="px-2 text-xs text-slate-500">{{ pageNum + 1 }} / {{ qaPage?.totalPages || 1 }}</span>
            <button
              type="button"
              class="rounded-md border border-slate-200 px-3 py-1.5 text-xs font-medium text-slate-600 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40"
              :disabled="!qaPage || pageNum >= qaPage.totalPages - 1"
              @click="goPage(pageNum + 1)"
            >
              다음
            </button>
          </div>
        </div>
      </template>
    </section>

    <NewProjectModal :open="projectModalOpen" @close="projectModalOpen = false" @created="onProjectCreated" />
    <NewQAModal
      :open="qaModalOpen"
      :projects="projects"
      :updates="updates"
      :members="members"
      @close="qaModalOpen = false"
      @created="onQaCreated"
    />
    <TeamsSetupNotice
      :open="teamsNoticeOpen"
      @close="teamsNoticeOpen = false"
      @confirm="onTeamsNoticeConfirm"
    />
  </section>
</template>
