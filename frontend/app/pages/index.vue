<script setup lang="ts">
import { FolderPlus, Plus } from '@lucide/vue'
import QaStatusSummary from '~/components/feature/QaStatusSummary.vue'
import QAList from '~/components/feature/QAList.vue'
import NewProjectModal from '~/components/feature/NewProjectModal.vue'
import NewQAModal from '~/components/feature/NewQAModal.vue'
import TeamsSetupNotice from '~/components/feature/TeamsSetupNotice.vue'
import AppSelect from '~/components/base/AppSelect.vue'
import type { Member, Project, ProjectUpdate, QaPage, QaDashboardStats } from '~/types/api'

/**
 * 대시보드. 프로젝트 목록은 사이드바로 옮겨졌고, 여기는 QA 현황 요약 + 전체 QA 목록만 보여준다.
 */
const projectsApi = useProjects()
const updatesApi = useUpdates()
const qaApi = useQa()
const membersApi = useMembers()
const auth = useAuthStore()
const sidebar = useSidebarStore()
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
const { t } = useI18n()
const pageSizeOptions = computed(() => [10, 50, 100].map((n) => ({ value: n, label: t('dashboard.qaList.pageSizeOption', { n }) })))

// 대시보드 수치는 집계 API 사용.
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
    updates.value = await updatesApi.listAll()
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
  router.push('/settings/notifications#teams')
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

watch(pageSize, async () => {
  pageNum.value = 0
  await loadQaPage()
})

async function goPage(p: number) {
  pageNum.value = p
  await loadQaPage()
}

async function onProjectCreated(p: Project) {
  projects.value.unshift(p)
  projectModalOpen.value = false
  await sidebar.reload()
  router.push(`/project/${p.id}`)
}
async function onQaCreated() {
  // 서버 페이징/집계 기준과 어긋나지 않게 재조회한다.
  myStats.value = null
  overallStats.value = await qaApi.dashboardStats()
  if (myOnly.value) myStats.value = await qaApi.dashboardStats(true)
  await loadQaPage()
  void sidebar.reload()
}
</script>

<template>
  <section>
    <header class="mb-6 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
      <div>
        <h1 class="text-xl font-bold text-slate-800 md:text-2xl dark:text-slate-100">{{ $t('dashboard.header.title') }}</h1>
        <p class="mt-1 text-sm text-slate-500 dark:text-slate-400">{{ $t('dashboard.header.subtitle') }}</p>
      </div>
      <div class="flex gap-2">
        <button
          type="button"
          class="inline-flex items-center gap-2 rounded-lg bg-emerald-500 px-4 py-2 text-sm font-medium text-white hover:bg-emerald-600"
          @click="projectModalOpen = true"
        >
          <FolderPlus class="h-4 w-4" />
          {{ $t('dashboard.header.newProject') }}
        </button>
        <button
          type="button"
          class="inline-flex items-center gap-2 rounded-lg bg-blue-500 px-4 py-2 text-sm font-medium text-white hover:bg-blue-600"
          @click="qaModalOpen = true"
        >
          <Plus class="h-4 w-4" />
          {{ $t('dashboard.header.newQaItem') }}
        </button>
      </div>
    </header>

    <section class="mb-6">
      <div class="mb-3 flex items-center justify-between">
        <h2 class="text-sm font-semibold text-slate-700 dark:text-slate-200">{{ $t('dashboard.stats.title') }}</h2>
        <label class="flex cursor-pointer select-none items-center gap-1.5 text-xs font-medium text-slate-500 dark:text-slate-400">
          <input
            v-model="myOnly"
            type="checkbox"
            class="h-3.5 w-3.5 rounded border-slate-300 text-blue-500 focus:ring-blue-400 dark:border-slate-600 dark:bg-slate-900"
          />
          {{ $t('dashboard.stats.myOnly') }}
        </label>
      </div>
      <div v-if="loading" class="h-[116px] animate-pulse rounded-xl border border-slate-200 bg-white px-6 py-5 dark:border-slate-800 dark:bg-slate-900">
        <div class="flex h-full items-center gap-6">
          <div class="h-10 w-10 rounded-lg bg-slate-100 dark:bg-slate-800/60" />
          <div class="space-y-2"><div class="h-3 w-16 rounded bg-slate-200 dark:bg-slate-800" /><div class="h-7 w-12 rounded bg-slate-200 dark:bg-slate-800" /></div>
          <div class="flex-1 space-y-3"><div class="h-2.5 w-full rounded-full bg-slate-100 dark:bg-slate-800/60" /><div class="h-3 w-2/3 rounded bg-slate-100 dark:bg-slate-800/60" /></div>
        </div>
      </div>
      <QaStatusSummary v-else :stats="stats" />
    </section>

    <section>
      <h2 class="mb-3 text-sm font-semibold text-slate-700 dark:text-slate-200">{{ $t('dashboard.qaList.title') }}</h2>
      <div v-if="loading" class="overflow-hidden rounded-xl border border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-900">
        <div v-for="i in 5" :key="i" class="flex animate-pulse items-center gap-4 border-b border-slate-100 px-4 py-4 last:border-0 dark:border-slate-800">
          <div class="h-3 w-16 rounded bg-slate-100 dark:bg-slate-800/60" />
          <div class="h-4 flex-1 rounded bg-slate-200 dark:bg-slate-800" />
          <div class="h-3 w-20 rounded bg-slate-100 dark:bg-slate-800/60" />
          <div class="h-5 w-16 rounded-full bg-slate-100 dark:bg-slate-800/60" />
        </div>
      </div>
      <template v-else>
        <QAList :items="qaPage?.content ?? []" :updates="updates" :members="members" :projects="projects" />
        <div class="mt-3 flex flex-wrap items-center justify-between gap-2">
          <div class="flex items-center gap-2 text-xs text-slate-500 dark:text-slate-400">
            <span>{{ $t('dashboard.qaList.perPage') }}</span>
            <AppSelect v-model="pageSize" size="xs" :options="pageSizeOptions" />
            <span v-if="qaPage">{{ $t('dashboard.qaList.totalCount', { count: qaPage.totalElements.toLocaleString() }) }}</span>
          </div>
          <div class="flex items-center gap-1">
            <button
              type="button"
              class="rounded-md border border-slate-200 px-3 py-1.5 text-xs font-medium text-slate-600 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-800/60"
              :disabled="pageNum === 0"
              @click="goPage(pageNum - 1)"
            >
              {{ $t('dashboard.qaList.prev') }}
            </button>
            <span class="px-2 text-xs text-slate-500 dark:text-slate-400">{{ pageNum + 1 }} / {{ qaPage?.totalPages || 1 }}</span>
            <button
              type="button"
              class="rounded-md border border-slate-200 px-3 py-1.5 text-xs font-medium text-slate-600 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-800/60"
              :disabled="!qaPage || pageNum >= qaPage.totalPages - 1"
              @click="goPage(pageNum + 1)"
            >
              {{ $t('dashboard.qaList.next') }}
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
