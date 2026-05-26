<script setup lang="ts">
import { FolderPlus, Plus, FileText, Clock, Loader, CheckCheck, AlertTriangle } from '@lucide/vue'
import StatsCard from '~/components/feature/StatsCard.vue'
import ProjectCard from '~/components/feature/ProjectCard.vue'
import QAList from '~/components/feature/QAList.vue'
import NewProjectModal from '~/components/feature/NewProjectModal.vue'
import NewQAModal from '~/components/feature/NewQAModal.vue'
import type { Member, Project, ProjectUpdate, QaItem } from '~/types/api'

const projectsApi = useProjects()
const updatesApi = useUpdates()
const qaApi = useQa()
const membersApi = useMembers()

const projects = ref<Project[]>([])
const updates = ref<ProjectUpdate[]>([])
const qaItems = ref<QaItem[]>([])
const members = ref<Member[]>([])
const loading = ref(true)
const projectModalOpen = ref(false)
const qaModalOpen = ref(false)

async function load() {
  loading.value = true
  try {
    projects.value = await projectsApi.list()
    qaItems.value = await qaApi.list()
    members.value = await membersApi.list()
    const lists = await Promise.all(projects.value.map((p) => updatesApi.listByProject(p.id)))
    updates.value = lists.flat()
  } finally {
    loading.value = false
  }
}

if (import.meta.client) {
  onMounted(load)
}

const stats = computed(() => {
  const totalQA = qaItems.value.length
  const resolved = qaItems.value.filter((q) => q.status === 'fix_done' || q.status === 'confirmed').length
  const inProgress = qaItems.value.filter((q) => q.status === 'in_progress').length
  const pending = qaItems.value.filter((q) => q.status === 'needs_fix').length
  const critical = qaItems.value.filter((q) => q.priority === 'critical').length
  return { totalQA, resolved, inProgress, pending, critical }
})

const projectQaStats = computed(() => {
  const updIdToProject = new Map<number, number>()
  for (const u of updates.value) updIdToProject.set(u.id, u.projectId)
  const stat = new Map<number, { count: number; resolved: number }>()
  for (const q of qaItems.value) {
    const pid = updIdToProject.get(q.updateId)
    if (pid == null) continue
    const s = stat.get(pid) ?? { count: 0, resolved: 0 }
    s.count += 1
    if (q.status === 'fix_done' || q.status === 'confirmed') s.resolved += 1
    stat.set(pid, s)
  }
  return stat
})

async function onTogglePin(id: number) {
  await projectsApi.togglePin(id)
  projects.value = await projectsApi.list()
}

function onProjectCreated(p: Project) {
  projects.value.unshift(p)
}
function onQaCreated(item: QaItem) {
  qaItems.value.unshift(item)
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

    <section class="mb-6 grid grid-cols-2 gap-3 md:grid-cols-3 md:gap-4 lg:grid-cols-5">
      <template v-if="loading">
        <div v-for="i in 5" :key="i" class="h-[96px] animate-pulse rounded-xl border border-slate-200 bg-white p-5">
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
        <StatsCard
          title="전체 QA"
          :value="stats.totalQA"
          :icon="FileText"
          icon-color="text-blue-500"
          icon-bg="bg-blue-50"
          :trend="`진행중 ${stats.inProgress}건`"
        />
        <StatsCard title="대기중" :value="stats.pending" :icon="Clock" icon-color="text-slate-500" icon-bg="bg-slate-100" />
        <StatsCard title="진행중" :value="stats.inProgress" :icon="Loader" icon-color="text-blue-500" icon-bg="bg-blue-50" />
        <StatsCard
          title="완료"
          :value="stats.resolved"
          :icon="CheckCheck"
          icon-color="text-emerald-500"
          icon-bg="bg-emerald-50"
          :trend="`완료율 ${stats.totalQA > 0 ? Math.round((stats.resolved / stats.totalQA) * 100) : 0}%`"
        />
        <StatsCard title="긴급" :value="stats.critical" :icon="AlertTriangle" icon-color="text-rose-500" icon-bg="bg-rose-50" />
      </template>
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
      <QAList v-else :items="qaItems" :updates="updates" :members="members" />
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
  </section>
</template>
