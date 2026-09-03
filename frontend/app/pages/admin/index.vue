<script setup lang="ts">
import {
  AlertTriangle,
  ArrowLeft,
  Bug,
  Folder,
  Loader2,
} from '@lucide/vue'
import StatsCard from '~/components/feature/StatsCard.vue'
import type { Project, ProjectStatus, ProjectUpdate, QaItem, QaPriority, QaStatus, QaStatusUpper } from '~/types/api'

const router = useRouter()
const projectsApi = useProjects()
const qaApi = useQa()
const updatesApi = useUpdates()
const auth = useAuthStore()
const { t } = useI18n()
const { confirmDialog, alertDialog } = useAppDialog()

// 관리자 전용 페이지 — 일반 멤버는 대시보드로 돌려보낸다 (API 는 백엔드 403 으로 별도 보호됨)
watchEffect(() => {
  if (auth.user && auth.user.accountRole !== 'ADMIN') router.replace('/')
})

type TabKey = 'projects' | 'qa'
const route = useRoute()
const TAB_KEYS: TabKey[] = ['projects', 'qa']
const initialTab = TAB_KEYS.includes(route.query.tab as TabKey) ? (route.query.tab as TabKey) : 'projects'
const activeTab = ref<TabKey>(initialTab)

const projects = ref<Project[]>([])
const qas = ref<QaItem[]>([])
const updates = ref<ProjectUpdate[]>([])
const loading = ref(true)

async function loadAll() {
  loading.value = true
  try {
    const [p, q, u] = await Promise.all([
      projectsApi.list(),
      qaApi.list(),
      updatesApi.listAll(),
    ])
    projects.value = p
    qas.value = q
    updates.value = u
  } finally {
    loading.value = false
  }
}
if (import.meta.client) onMounted(loadAll)

const stats = computed(() => ({
  totalProjects: projects.value.length,
  totalQA: qas.value.length,
  activeProjects: projects.value.filter((p) => p.status === 'active').length,
  criticalQA: qas.value.filter((q) => q.priority === 'critical' && q.status !== 'fix_done' && q.status !== 'confirmed').length,
}))

const updateToProject = computed(() => {
  const map = new Map<number, number>()
  for (const u of updates.value) map.set(u.id, u.projectId)
  return map
})

function projectName(updateId: number) {
  const projectId = updateToProject.value.get(updateId)
  return projects.value.find((p) => p.id === projectId)?.name ?? '-'
}

function projectQaCount(projectId: number) {
  const updateIds = updates.value.filter((u) => u.projectId === projectId).map((u) => u.id)
  return qas.value.filter((q) => updateIds.includes(q.updateId)).length
}


const projectStatusConfig: Record<ProjectStatus, { label: string; color: string; bg: string }> = {
  active: { label: 'common.projectStatus.active', color: 'text-emerald-600 dark:text-emerald-400', bg: 'bg-emerald-50 dark:bg-emerald-500/10' },
  completed: { label: 'common.projectStatus.completed', color: 'text-slate-500 dark:text-slate-400', bg: 'bg-slate-50 dark:bg-slate-800/60' },
  paused: { label: 'common.projectStatus.paused', color: 'text-amber-600 dark:text-amber-400', bg: 'bg-amber-50 dark:bg-amber-500/10' },
}

const qaStatusConfig: Record<QaStatus, { label: string; color: string; bg: string }> = {
  needs_fix:     { label: 'common.qaStatus.needs_fix',     color: 'text-rose-600 dark:text-rose-400',       bg: 'bg-rose-50 dark:bg-rose-500/10' },
  in_progress:   { label: 'common.qaStatus.in_progress',   color: 'text-blue-600 dark:text-blue-400',       bg: 'bg-blue-50 dark:bg-blue-500/10' },
  fix_done:      { label: 'common.qaStatus.fix_done',      color: 'text-amber-600 dark:text-amber-400',     bg: 'bg-amber-50 dark:bg-amber-500/10' },
  confirmed:     { label: 'common.qaStatus.confirmed',     color: 'text-emerald-600 dark:text-emerald-400', bg: 'bg-emerald-50 dark:bg-emerald-500/10' },
  on_hold:       { label: 'common.qaStatus.on_hold',       color: 'text-slate-600 dark:text-slate-300',     bg: 'bg-slate-100 dark:bg-slate-800' },
  needs_recheck: { label: 'common.qaStatus.needs_recheck', color: 'text-purple-600 dark:text-purple-400',   bg: 'bg-purple-50 dark:bg-purple-500/10' },
}

const priorityConfig: Record<QaPriority, { label: string; color: string }> = {
  low: { label: 'common.priority.low', color: 'text-slate-500 dark:text-slate-400' },
  medium: { label: 'common.priority.medium', color: 'text-amber-500 dark:text-amber-400' },
  high: { label: 'common.priority.high', color: 'text-orange-500 dark:text-orange-400' },
  critical: { label: 'common.priority.critical', color: 'text-rose-500 dark:text-rose-400' },
}

function statusToCode(s: string): 'ACTIVE' | 'COMPLETED' | 'PAUSED' {
  return s.toUpperCase() as 'ACTIVE' | 'COMPLETED' | 'PAUSED'
}
function qaStatusToCode(s: string): QaStatusUpper {
  return s.toUpperCase() as QaStatusUpper
}

async function changeProjectStatus(p: Project, next: ProjectStatus) {
  const updated = await projectsApi.update(p.id, { status: statusToCode(next) })
  projects.value = projects.value.map((x) => (x.id === p.id ? updated : x))
}
async function changeQaStatus(q: QaItem, next: QaStatus) {
  const updated = await qaApi.update(q.id, { status: qaStatusToCode(next) })
  qas.value = qas.value.map((x) => (x.id === q.id ? updated : x))
}
</script>

<template>
  <section>
    <!-- Header -->
    <header class="mb-6 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between md:mb-8">
      <div>
        <h1 class="text-xl font-bold text-slate-800 md:text-2xl dark:text-slate-100">{{ $t('admin.title') }}</h1>
        <p class="mt-1 text-sm text-slate-500 dark:text-slate-400">{{ $t('admin.subtitle') }}</p>
      </div>
      <button
        type="button"
        class="inline-flex items-center gap-2 whitespace-nowrap rounded-lg border border-slate-200 bg-white px-4 py-2 text-sm font-medium text-slate-600 hover:bg-slate-50 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-300 dark:hover:bg-slate-800/60"
        @click="router.push('/')"
      >
        <ArrowLeft class="h-4 w-4" />
        {{ $t('admin.backToDashboard') }}
      </button>
    </header>

    <!-- Stats -->
    <div class="mb-6 grid grid-cols-2 gap-3 md:mb-8 md:grid-cols-4 md:gap-4">
      <template v-if="loading">
        <div v-for="i in 4" :key="i" class="h-[96px] animate-pulse rounded-xl border border-slate-200 bg-white p-5 dark:border-slate-800 dark:bg-slate-900">
          <div class="flex items-start justify-between">
            <div class="space-y-2">
              <div class="h-3 w-20 rounded bg-slate-200 dark:bg-slate-800" />
              <div class="h-7 w-12 rounded bg-slate-200 dark:bg-slate-800" />
              <div class="h-3 w-16 rounded bg-slate-100 dark:bg-slate-800/60" />
            </div>
            <div class="h-10 w-10 rounded-lg bg-slate-100 dark:bg-slate-800/60" />
          </div>
        </div>
      </template>
      <template v-else>
        <StatsCard
          :title="$t('admin.stats.totalProjects')"
          :value="stats.totalProjects"
          :icon="Folder"
          icon-color="text-emerald-500 dark:text-emerald-400"
          icon-bg="bg-emerald-50 dark:bg-emerald-500/10"
          :trend="$t('admin.stats.activeTrend', { n: stats.activeProjects })"
        />
        <StatsCard
          :title="$t('admin.stats.totalQa')"
          :value="stats.totalQA"
          :icon="Bug"
          icon-color="text-rose-500 dark:text-rose-400"
          icon-bg="bg-rose-50 dark:bg-rose-500/10"
        />
        <StatsCard
          :title="$t('admin.stats.activeProjects')"
          :value="stats.activeProjects"
          :icon="Loader2"
          icon-color="text-emerald-500 dark:text-emerald-400"
          icon-bg="bg-emerald-50 dark:bg-emerald-500/10"
        />
        <StatsCard
          :title="$t('admin.stats.criticalQa')"
          :value="stats.criticalQA"
          :icon="AlertTriangle"
          icon-color="text-red-500 dark:text-red-400"
          icon-bg="bg-red-50 dark:bg-red-500/10"
        />
      </template>
    </div>

    <!-- Tabs -->
    <div class="mb-4 flex items-center justify-between">
      <div class="flex w-fit items-center gap-1 rounded-lg bg-slate-100 p-1 dark:bg-slate-800">
        <button
          v-for="tab in [
            { key: 'projects' as TabKey, label: 'admin.tabs.projects', icon: Folder },
            { key: 'qa' as TabKey, label: 'admin.tabs.qa', icon: Bug },
          ]"
          :key="tab.key"
          type="button"
          :class="[
            'flex items-center gap-1.5 rounded-md px-4 py-2 text-sm font-medium transition-colors whitespace-nowrap',
            activeTab === tab.key ? 'bg-white text-slate-800 shadow-sm dark:bg-slate-900 dark:text-slate-100' : 'text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-200',
          ]"
          @click="activeTab = tab.key"
        >
          <component :is="tab.icon" class="h-4 w-4" />
          {{ $t(tab.label) }}
        </button>
      </div>
    </div>

    <!-- Content -->
    <div class="overflow-hidden rounded-xl border border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-900">
      <div v-if="loading" class="overflow-x-auto">
        <table class="w-full">
          <thead class="border-b border-slate-200 bg-slate-50 dark:border-slate-800 dark:bg-slate-800/50">
            <tr>
              <th v-for="i in 5" :key="i" class="px-4 py-3"><div class="h-3 w-16 animate-pulse rounded bg-slate-200 dark:bg-slate-800" /></th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-100 dark:divide-slate-800">
            <tr v-for="r in 6" :key="r">
              <td v-for="c in 5" :key="c" class="px-4 py-4"><div class="h-4 animate-pulse rounded bg-slate-100 dark:bg-slate-800/60" :style="{ width: `${40 + ((r * c) % 5) * 15}%` }" /></td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Projects -->
      <div v-else-if="activeTab === 'projects'" class="overflow-x-auto">
        <table class="w-full text-left text-sm">
          <thead class="border-b border-slate-200 bg-slate-50 dark:border-slate-800 dark:bg-slate-800/50">
            <tr>
              <th class="px-4 py-3 text-xs font-semibold text-slate-500 dark:text-slate-400">{{ $t('admin.projects.nameHeader') }}</th>
              <th class="w-36 px-4 py-3 text-xs font-semibold text-slate-500 dark:text-slate-400">{{ $t('admin.table.status') }}</th>
              <th class="w-24 px-4 py-3 text-xs font-semibold text-slate-500 dark:text-slate-400">{{ $t('admin.projects.qaCountHeader') }}</th>
              <th class="w-32 px-4 py-3 text-xs font-semibold text-slate-500 dark:text-slate-400">{{ $t('admin.projects.createdAtHeader') }}</th>
              <th class="w-24 px-4 py-3 text-xs font-semibold text-slate-500 dark:text-slate-400">{{ $t('admin.table.actions') }}</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-100 dark:divide-slate-800">
            <tr v-for="p in projects" :key="p.id" class="transition-colors hover:bg-slate-50 dark:hover:bg-slate-800/60">
              <td class="px-4 py-3 font-medium text-slate-800 dark:text-slate-100">{{ p.name }}</td>
              <td class="px-4 py-3">
                <select
                  :value="p.status"
                  :class="['cursor-pointer rounded-full border-0 px-2 py-1 text-xs font-medium outline-none', projectStatusConfig[p.status].bg, projectStatusConfig[p.status].color]"
                  @change="changeProjectStatus(p, ($event.target as HTMLSelectElement).value as ProjectStatus)"
                >
                  <option value="active">{{ $t(projectStatusConfig.active.label) }}</option>
                  <option value="paused">{{ $t(projectStatusConfig.paused.label) }}</option>
                  <option value="completed">{{ $t(projectStatusConfig.completed.label) }}</option>
                </select>
              </td>
              <td class="px-4 py-3 text-slate-600 dark:text-slate-300">{{ projectQaCount(p.id) }}</td>
              <td class="px-4 py-3 text-slate-500 dark:text-slate-400">{{ p.createdAt ? p.createdAt.slice(0, 10) : '-' }}</td>
              <td class="px-4 py-3">
                <button
                  type="button"
                  class="text-xs font-medium text-emerald-500 hover:text-emerald-600 dark:text-emerald-400 dark:hover:text-emerald-300"
                  @click="router.push(`/project/${p.id}`)"
                >{{ $t('admin.table.viewDetail') }}</button>
              </td>
            </tr>
            <tr v-if="projects.length === 0">
              <td colspan="5" class="px-4 py-8 text-center text-sm text-slate-400 dark:text-slate-500">{{ $t('admin.projects.empty') }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- QA -->
      <div v-else-if="activeTab === 'qa'" class="overflow-x-auto">
        <table class="w-full text-left text-sm">
          <thead class="border-b border-slate-200 bg-slate-50 dark:border-slate-800 dark:bg-slate-800/50">
            <tr>
              <th class="px-4 py-3 text-xs font-semibold text-slate-500 dark:text-slate-400">{{ $t('admin.qa.titleHeader') }}</th>
              <th class="w-40 px-4 py-3 text-xs font-semibold text-slate-500 dark:text-slate-400">{{ $t('admin.qa.projectHeader') }}</th>
              <th class="w-28 px-4 py-3 text-xs font-semibold text-slate-500 dark:text-slate-400">{{ $t('admin.qa.priorityHeader') }}</th>
              <th class="w-32 px-4 py-3 text-xs font-semibold text-slate-500 dark:text-slate-400">{{ $t('admin.table.status') }}</th>
              <th class="w-24 px-4 py-3 text-xs font-semibold text-slate-500 dark:text-slate-400">{{ $t('common.roles.assignee') }}</th>
              <th class="w-24 px-4 py-3 text-xs font-semibold text-slate-500 dark:text-slate-400">{{ $t('admin.table.actions') }}</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-100 dark:divide-slate-800">
            <tr v-for="q in qas" :key="q.id" class="transition-colors hover:bg-slate-50 dark:hover:bg-slate-800/60">
              <td class="max-w-xs truncate px-4 py-3 font-medium text-slate-800 dark:text-slate-100">{{ q.title }}</td>
              <td class="px-4 py-3 text-slate-600 dark:text-slate-300">{{ projectName(q.updateId) }}</td>
              <td class="px-4 py-3">
                <span :class="['text-xs font-semibold', priorityConfig[q.priority].color]">{{ $t(priorityConfig[q.priority].label) }}</span>
              </td>
              <td class="px-4 py-3">
                <select
                  :value="q.status"
                  :class="['cursor-pointer rounded-full border-0 px-2 py-1 text-xs font-medium outline-none', qaStatusConfig[q.status].bg, qaStatusConfig[q.status].color]"
                  @change="changeQaStatus(q, ($event.target as HTMLSelectElement).value as QaStatus)"
                >
                  <option value="needs_fix">{{ $t(qaStatusConfig.needs_fix.label) }}</option>
                  <option value="in_progress">{{ $t(qaStatusConfig.in_progress.label) }}</option>
                  <option value="fix_done">{{ $t(qaStatusConfig.fix_done.label) }}</option>
                  <option value="confirmed">{{ $t(qaStatusConfig.confirmed.label) }}</option>
                  <option value="on_hold">{{ $t(qaStatusConfig.on_hold.label) }}</option>
                  <option value="needs_recheck">{{ $t(qaStatusConfig.needs_recheck.label) }}</option>
                </select>
              </td>
              <td class="px-4 py-3 text-xs text-slate-600 dark:text-slate-300">
                <div class="flex flex-col gap-0.5">
                  <span v-if="q.tester" class="text-slate-500 dark:text-slate-400">T: {{ q.tester.name }}</span>
                  <span v-if="q.assignee1 || q.assignee2">
                    {{ [q.assignee1?.name, q.assignee2?.name].filter(Boolean).join(', ') }}
                  </span>
                  <span v-if="!q.tester && !q.assignee1 && !q.assignee2" class="text-slate-400 dark:text-slate-500">-</span>
                </div>
              </td>
              <td class="px-4 py-3">
                <button
                  type="button"
                  class="text-xs font-medium text-emerald-500 hover:text-emerald-600 dark:text-emerald-400 dark:hover:text-emerald-300"
                  @click="router.push(`/qa/${q.id}`)"
                >{{ $t('admin.table.viewDetail') }}</button>
              </td>
            </tr>
            <tr v-if="qas.length === 0">
              <td colspan="6" class="px-4 py-8 text-center text-sm text-slate-400 dark:text-slate-500">{{ $t('admin.qa.empty') }}</td>
            </tr>
          </tbody>
        </table>
      </div>

    </div>
  </section>
</template>
