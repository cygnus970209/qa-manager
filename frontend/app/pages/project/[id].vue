<script setup lang="ts">
import { Plus, ArrowUpDown } from '@lucide/vue'
import ProjectHeader from '~/components/feature/ProjectHeader.vue'
import UpdateAccordion from '~/components/feature/UpdateAccordion.vue'
import QaStatusSummary from '~/components/feature/QaStatusSummary.vue'
import NewUpdateModal from '~/components/feature/NewUpdateModal.vue'
import ReorderUpdateModal from '~/components/feature/ReorderUpdateModal.vue'
import NewProjectModal from '~/components/feature/NewProjectModal.vue'
import NewQAModal from '~/components/feature/NewQAModal.vue'
import DeleteConfirmModal from '~/components/base/DeleteConfirmModal.vue'
import TestCaseListView from '~/components/feature/testing/TestCaseListView.vue'
import TestFlowView from '~/components/feature/testing/TestFlowView.vue'
import NewTestRunModal from '~/components/feature/testing/NewTestRunModal.vue'
import type { Member, Project, ProjectUpdate, QaItem, QaStatusUpper, TestRun, TestRunDetail } from '~/types/api'

const route = useRoute()
const router = useRouter()
const projectId = computed(() => Number(route.params.id))

const projectsApi = useProjects()
const updatesApi = useUpdates()
const qaApi = useQa()
const membersApi = useMembers()
const auth = useAuthStore()
const sidebar = useSidebarStore()
const { t } = useI18n()

const project = ref<Project | null>(null)
const updates = ref<ProjectUpdate[]>([])
const items = ref<QaItem[]>([])
const members = ref<Member[]>([])
const allProjects = ref<Project[]>([])
const loading = ref(true)
const error = ref<string | null>(null)

const updateModalOpen = ref(false)
const reorderModalOpen = ref(false)
const qaModalOpen = ref(false)
const qaDefaultUpdateId = ref<number | undefined>()

const projectEditOpen = ref(false)
const projectDeleteOpen = ref(false)

const updateEditOpen = ref(false)
const updateEditTarget = ref<ProjectUpdate | null>(null)
const updateDeleteOpen = ref(false)
const updateDeleteTarget = ref<ProjectUpdate | null>(null)

/* ─────────────── 화면: 개요(qa) | 테스트 케이스(tests) | 테스트 런(runs) ───────────────
 * 앱 사이드바의 프로젝트 하위 메뉴가 쿼리(tab, view)로 고른다. */
type ProjectTab = 'qa' | 'tests' | 'runs'
const activeTab = computed<ProjectTab>(() => {
  const tab = route.query.tab
  return tab === 'tests' ? 'tests' : tab === 'runs' ? 'runs' : 'qa'
})
/** 테스트 케이스 화면 내부 뷰: 리스트 | 플로우(그래프) */
const testView = computed<'list' | 'flow'>(() => (route.query.view === 'flow' ? 'flow' : 'list'))

/* ─────────────── 테스트 런 (업데이트 아코디언 요약) ─────────────── */
const testingApi = useTesting()
const runs = ref<TestRun[]>([])
const runsByUpdate = computed(() => {
  const map = new Map<number, TestRun[]>()
  for (const r of runs.value) {
    const arr = map.get(r.updateId) ?? []
    arr.push(r)
    map.set(r.updateId, arr)
  }
  return map
})
async function loadRuns() {
  try {
    runs.value = await testingApi.listRunsByProject(projectId.value)
  } catch {
    runs.value = []
  }
}
/** 테스트 런 목록 화면용: 최근 생성 순 */
const sortedRuns = computed(() => [...runs.value].sort((a, b) => b.createdAt.localeCompare(a.createdAt)))
function updateVersion(updateId: number) {
  return updates.value.find((u) => u.id === updateId)?.version ?? '-'
}

const runModalOpen = ref(false)
const runModalUpdate = ref<ProjectUpdate | null>(null)
function onNewRun(updateId: number) {
  runModalUpdate.value = updates.value.find((u) => u.id === updateId) ?? null
  if (runModalUpdate.value) runModalOpen.value = true
}
function onRunCreated(detail: TestRunDetail) {
  runModalOpen.value = false
  runModalUpdate.value = null
  loadRuns()
  router.push(`/run/${detail.run.id}`)
}

async function load() {
  loading.value = true
  error.value = null
  try {
    project.value = await projectsApi.get(projectId.value)
    updates.value = await updatesApi.listByProject(projectId.value)
    const updateIds = new Set(updates.value.map((u) => u.id))
    const all = await qaApi.list()
    items.value = all.filter((q) => updateIds.has(q.updateId))
    // 모달용 보조 데이터
    members.value = await membersApi.list()
    allProjects.value = await projectsApi.list()
    await loadRuns()
  } catch (e: any) {
    error.value = e?.data?.message ?? t('project.errors.loadFailed')
  } finally {
    loading.value = false
  }
}

watch(projectId, () => { if (import.meta.client) load() })
if (import.meta.client) onMounted(load)

// 프로젝트를 열면 사이드바의 새 알림 배지를 지운다. 보는 동안 온 알림도 나갈 때 확인한 것으로 본다.
if (import.meta.client) {
  watch(projectId, (id, prev) => {
    if (prev != null) void sidebar.markSeen(prev)
    void sidebar.markSeen(id)
  }, { immediate: true })
  onBeforeUnmount(() => { void sidebar.markSeen(projectId.value) })
}

// '내 작업만' 체크 시 내가 테스터/담당자로 지정된 QA 만 수치에 반영한다.
const myOnly = ref(false)
function isMine(q: QaItem) {
  const uid = auth.user?.id
  return uid != null && [q.tester?.id, q.assignee1?.id, q.assignee2?.id].includes(uid)
}
const statsItems = computed(() => (myOnly.value ? items.value.filter(isMine) : items.value))

const stats = computed(() => {
  const list = statsItems.value
  const count = (s: QaItem['status']) => list.filter((q) => q.status === s).length
  return {
    total: list.length,
    needsFix: count('needs_fix'),
    inProgress: count('in_progress'),
    fixDone: count('fix_done'),
    confirmed: count('confirmed'),
    onHold: count('on_hold'),
    needsRecheck: count('needs_recheck'),
    critical: list.filter((q) => q.priority === 'critical').length,
  }
})

// 프로젝트 헤더/목록 요약은 '내 작업만' 필터와 무관하게 전체 기준을 유지한다.
const resolvedAll = computed(
  () => items.value.filter((q) => q.status === 'fix_done' || q.status === 'confirmed').length,
)

/* '배포완료 숨기기' 토글 (localStorage 영속) */
const HIDE_RELEASED_KEY = 'project-hide-released-updates'
const hideReleased = ref(false)
if (import.meta.client) {
  onMounted(() => {
    const v = localStorage.getItem(HIDE_RELEASED_KEY)
    if (v !== null) hideReleased.value = v === '1'
  })
}
watch(hideReleased, (v) => localStorage.setItem(HIDE_RELEASED_KEY, v ? '1' : '0'))

const visibleUpdates = computed(() =>
  hideReleased.value ? updates.value.filter((u) => u.status !== 'released') : updates.value,
)
const hiddenReleasedCount = computed(() => updates.value.length - visibleUpdates.value.length)

const itemsByUpdate = computed(() => {
  const map = new Map<number, QaItem[]>()
  for (const q of items.value) {
    const arr = map.get(q.updateId) ?? []
    arr.push(q)
    map.set(q.updateId, arr)
  }
  return map
})

// 사이드바 우클릭 메뉴로 고정을 바꾸면 헤더의 핀 상태도 따라간다
watch(() => sidebar.projects.find((p) => p.id === projectId.value)?.pinned, (pinned) => {
  if (pinned != null && project.value && project.value.pinned !== pinned) {
    project.value = { ...project.value, pinned }
  }
})

/** 상단 고정 토글 — 사이드바 정렬(고정 우선)에 바로 반영 */
async function onTogglePin() {
  if (!project.value) return
  await projectsApi.togglePin(project.value.id)
  project.value = await projectsApi.get(project.value.id)
  void sidebar.reload()
}
async function onProjectStatus(status: 'ACTIVE' | 'PAUSED' | 'COMPLETED') {
  if (!project.value) return
  project.value = await projectsApi.update(project.value.id, { status })
  void sidebar.reload()
}
async function onUpdateStatus(updateId: number, status: 'IN_PROGRESS' | 'TESTING' | 'RELEASED') {
  const updated = await updatesApi.update(updateId, { status })
  updates.value = updates.value.map((u) => u.id === updateId ? updated : u)
}
async function onQaStatusChange(qaId: number, status: QaStatusUpper) {
  const updated = await qaApi.update(qaId, { status })
  items.value = items.value.map((q) => (q.id === qaId ? updated : q))
  void sidebar.reload()
}
function onAddInlineQa(updateId: number) {
  qaDefaultUpdateId.value = updateId
  qaModalOpen.value = true
}
function onUpdateCreated(u: ProjectUpdate) {
  updates.value = [u, ...updates.value]
}
function onUpdatesReordered(reordered: ProjectUpdate[]) {
  updates.value = reordered
}
function onQaCreated(q: QaItem) {
  items.value = [q, ...items.value]
  void sidebar.reload()
}

function onProjectEdited(updated: Project) {
  project.value = updated
  void sidebar.reload()
}
async function confirmProjectDelete() {
  if (!project.value) return
  await projectsApi.remove(project.value.id)
  projectDeleteOpen.value = false
  await sidebar.reload()
  router.push('/')
}

function openUpdateEdit(u: ProjectUpdate) {
  updateEditTarget.value = u
  updateEditOpen.value = true
}
function onUpdateEdited(u: ProjectUpdate) {
  updates.value = updates.value.map((x) => (x.id === u.id ? u : x))
}
function openUpdateDelete(u: ProjectUpdate) {
  updateDeleteTarget.value = u
  updateDeleteOpen.value = true
}
async function confirmUpdateDelete() {
  if (!updateDeleteTarget.value) return
  const id = updateDeleteTarget.value.id
  await updatesApi.remove(id)
  updates.value = updates.value.filter((u) => u.id !== id)
  // 해당 업데이트에 속한 QA 항목도 화면에서 즉시 제거 (백엔드 cascade 정책에 따름)
  items.value = items.value.filter((q) => q.updateId !== id)
  updateDeleteOpen.value = false
  updateDeleteTarget.value = null
}
</script>

<template>
  <section>
    <template v-if="loading">
      <!-- Project header skeleton -->
      <div class="animate-pulse rounded-xl border border-slate-200 bg-white p-5 dark:border-slate-800 dark:bg-slate-900">
        <div class="flex items-start justify-between">
          <div class="space-y-2">
            <div class="h-6 w-48 rounded bg-slate-200 dark:bg-slate-800" />
            <div class="h-4 w-72 rounded bg-slate-100 dark:bg-slate-800/60" />
            <div class="mt-2 flex gap-2">
              <div class="h-5 w-16 rounded-full bg-slate-100 dark:bg-slate-800/60" />
              <div class="h-5 w-20 rounded-full bg-slate-100 dark:bg-slate-800/60" />
            </div>
          </div>
          <div class="h-8 w-24 rounded-md bg-slate-100 dark:bg-slate-800/60" />
        </div>
      </div>
      <!-- Stats skeleton -->
      <div class="mt-6 h-[116px] animate-pulse rounded-xl border border-slate-200 bg-white px-6 py-5 dark:border-slate-800 dark:bg-slate-900">
        <div class="flex h-full items-center gap-6">
          <div class="h-10 w-10 rounded-lg bg-slate-100 dark:bg-slate-800/60" />
          <div class="space-y-2"><div class="h-3 w-16 rounded bg-slate-200 dark:bg-slate-800" /><div class="h-7 w-12 rounded bg-slate-200 dark:bg-slate-800" /></div>
          <div class="flex-1 space-y-3"><div class="h-2.5 w-full rounded-full bg-slate-100 dark:bg-slate-800/60" /><div class="h-3 w-2/3 rounded bg-slate-100 dark:bg-slate-800/60" /></div>
        </div>
      </div>
      <!-- Updates skeleton -->
      <section class="mt-6 space-y-3">
        <div class="h-4 w-32 animate-pulse rounded bg-slate-200 dark:bg-slate-800" />
        <div v-for="i in 3" :key="i" class="animate-pulse rounded-xl border border-slate-200 bg-white p-4 dark:border-slate-800 dark:bg-slate-900">
          <div class="flex items-center justify-between">
            <div class="space-y-1.5">
              <div class="h-4 w-40 rounded bg-slate-200 dark:bg-slate-800" />
              <div class="h-3 w-24 rounded bg-slate-100 dark:bg-slate-800/60" />
            </div>
            <div class="h-6 w-20 rounded-full bg-slate-100 dark:bg-slate-800/60" />
          </div>
        </div>
      </section>
    </template>
    <div v-else-if="error" class="rounded-md bg-red-50 px-4 py-3 text-sm text-red-700 dark:bg-red-500/10 dark:text-red-300">
      {{ error }}
      <button class="ml-2 underline" @click="router.push('/')">{{ $t('project.errors.goToDashboard') }}</button>
    </div>
    <template v-else-if="project">
      <ProjectHeader
        :project="project"
        :total-q-a="items.length"
        :resolved-count="resolvedAll"
        :update-count="updates.length"
        @change-status="onProjectStatus"
        @edit="projectEditOpen = true"
        @remove="projectDeleteOpen = true"
        @toggle-pin="onTogglePin"
      />

      <!-- 테스트 케이스 화면 (사이드바: 테스트 케이스 / 테스트 플로우) -->
      <section v-if="activeTab === 'tests'" class="mt-6">
        <TestCaseListView v-if="testView === 'list'" :project-id="project.id" />
        <TestFlowView v-else :project-id="project.id" :updates="updates" />
      </section>

      <!-- 테스트 런 화면 -->
      <section v-else-if="activeTab === 'runs'" class="mt-6">
        <div class="mb-3 flex items-center justify-between">
          <h2 class="text-sm font-semibold text-slate-700 dark:text-slate-200">{{ $t('project.runs.title') }}</h2>
          <span class="text-xs text-slate-400 dark:text-slate-500">{{ $t('project.runs.count', runs.length) }}</span>
        </div>
        <div class="overflow-hidden rounded-xl border border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-900">
          <table class="w-full text-left text-sm">
            <thead class="border-b border-slate-100 bg-slate-50 dark:border-slate-800 dark:bg-slate-800/50">
              <tr>
                <th class="px-5 py-3.5 text-xs font-medium text-slate-500 dark:text-slate-400">{{ $t('project.runs.colName') }}</th>
                <th class="w-40 px-5 py-3.5 text-xs font-medium text-slate-500 dark:text-slate-400">{{ $t('project.runs.colUpdate') }}</th>
                <th class="w-28 px-5 py-3.5 text-xs font-medium text-slate-500 dark:text-slate-400">{{ $t('project.runs.colStatus') }}</th>
                <th class="w-32 px-5 py-3.5 text-xs font-medium text-slate-500 dark:text-slate-400">{{ $t('project.runs.colCreated') }}</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-50 dark:divide-slate-800">
              <tr
                v-for="r in sortedRuns"
                :key="r.id"
                class="cursor-pointer transition hover:bg-slate-50 dark:hover:bg-slate-800/60"
                @click="router.push(`/run/${r.id}`)"
              >
                <td class="px-5 py-3.5 font-medium text-slate-800 dark:text-slate-100">{{ r.name }}</td>
                <td class="px-5 py-3.5">
                  <span class="rounded bg-slate-100 px-2 py-0.5 text-xs text-slate-500 dark:bg-slate-800 dark:text-slate-400">{{ updateVersion(r.updateId) }}</span>
                </td>
                <td class="px-5 py-3.5">
                  <span
                    :class="[
                      'inline-flex items-center whitespace-nowrap rounded-full px-2.5 py-0.5 text-xs font-medium',
                      r.closedAt
                        ? 'bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-300'
                        : 'bg-blue-50 text-blue-600 dark:bg-blue-500/10 dark:text-blue-400',
                    ]"
                  >{{ r.closedAt ? $t('project.runs.closed') : $t('project.runs.open') }}</span>
                </td>
                <td class="px-5 py-3.5 text-xs text-slate-400 dark:text-slate-500">{{ r.createdAt.slice(0, 10) }}</td>
              </tr>
              <tr v-if="runs.length === 0">
                <td colspan="4" class="px-5 py-10 text-center text-sm text-slate-400 dark:text-slate-500">{{ $t('project.runs.empty') }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <div v-show="activeTab === 'qa'">
      <section class="mt-6">
        <div class="mb-3 flex items-center justify-between">
          <h2 class="text-sm font-semibold text-slate-700 dark:text-slate-200">{{ $t('project.stats.title') }}</h2>
          <label class="flex cursor-pointer select-none items-center gap-1.5 text-xs font-medium text-slate-500 dark:text-slate-400">
            <input
              v-model="myOnly"
              type="checkbox"
              class="h-3.5 w-3.5 rounded border-slate-300 text-blue-500 focus:ring-blue-400 dark:border-slate-600 dark:bg-slate-900"
            />
            {{ $t('project.stats.myOnly') }}
          </label>
        </div>
        <QaStatusSummary :stats="stats" />
      </section>

      <section class="mt-6">
        <div class="mb-3 flex items-center justify-between">
          <h2 class="text-sm font-semibold text-slate-700 dark:text-slate-200">{{ $t('project.updates.title') }}</h2>
          <div class="flex items-center gap-2 text-xs text-slate-400 dark:text-slate-500">
            <label class="flex cursor-pointer select-none items-center gap-1.5 font-medium text-slate-500 dark:text-slate-400">
              <input
                v-model="hideReleased"
                type="checkbox"
                class="h-3.5 w-3.5 rounded border-slate-300 text-blue-500 focus:ring-blue-400 dark:border-slate-600 dark:bg-slate-900"
              />
              {{ $t('project.updates.hideReleased') }}
            </label>
            <span>{{ $t('project.updates.updateCount', updates.length) }}{{ hiddenReleasedCount > 0 ? ` (${$t('project.updates.hiddenCount', { n: hiddenReleasedCount })})` : '' }} · {{ $t('project.updates.qaCount', items.length) }}</span>
            <button
              v-if="updates.length > 1"
              type="button"
              class="inline-flex items-center gap-1 rounded-md border border-slate-200 px-3 py-1.5 text-xs font-medium text-slate-600 hover:bg-slate-50 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-800/60"
              @click="reorderModalOpen = true"
            >
              <ArrowUpDown class="h-3.5 w-3.5" /> {{ $t('project.updates.reorder') }}
            </button>
            <button
              type="button"
              class="inline-flex items-center gap-1 rounded-md bg-emerald-500 px-3 py-1.5 text-xs font-medium text-white hover:bg-emerald-600"
              @click="updateModalOpen = true"
            >
              <Plus class="h-3.5 w-3.5" /> {{ $t('project.updates.newUpdate') }}
            </button>
            <button
              type="button"
              class="inline-flex items-center gap-1 rounded-md bg-blue-500 px-3 py-1.5 text-xs font-medium text-white hover:bg-blue-600"
              @click="qaDefaultUpdateId = undefined; qaModalOpen = true"
            >
              <Plus class="h-3.5 w-3.5" /> {{ $t('project.updates.newQa') }}
            </button>
          </div>
        </div>

        <div v-if="updates.length === 0" class="rounded-lg border border-dashed border-slate-200 px-4 py-10 text-center text-sm text-slate-400 dark:border-slate-800 dark:text-slate-500">
          {{ $t('project.updates.empty') }}
        </div>
        <div v-else-if="visibleUpdates.length === 0" class="rounded-lg border border-dashed border-slate-200 px-4 py-10 text-center text-sm text-slate-400 dark:border-slate-800 dark:text-slate-500">
          {{ $t('project.updates.hiddenNotice', hiddenReleasedCount) }}
        </div>
        <div v-else class="flex flex-col gap-3">
          <UpdateAccordion
            v-for="u in visibleUpdates"
            :key="u.id"
            :update="u"
            :items="itemsByUpdate.get(u.id) ?? []"
            :runs="runsByUpdate.get(u.id) ?? []"
            :default-open="u === visibleUpdates[0]"
            @change-status="onUpdateStatus"
            @add-qa="onAddInlineQa"
            @new-run="onNewRun"
            @edit="openUpdateEdit"
            @remove="openUpdateDelete"
            @change-qa-status="onQaStatusChange"
          />
        </div>
      </section>
      </div>

      <NewTestRunModal
        v-if="runModalUpdate"
        :open="runModalOpen"
        :update-id="runModalUpdate.id"
        :project-id="project.id"
        :default-name="$t('project.runs.defaultRunName', { version: runModalUpdate.version })"
        @close="runModalOpen = false; runModalUpdate = null"
        @created="onRunCreated"
      />

      <NewUpdateModal
        :open="updateModalOpen"
        :project-id="project.id"
        @close="updateModalOpen = false"
        @created="onUpdateCreated"
      />
      <ReorderUpdateModal
        :open="reorderModalOpen"
        :project-id="project.id"
        :updates="updates"
        @close="reorderModalOpen = false"
        @reordered="onUpdatesReordered"
      />
      <NewUpdateModal
        :open="updateEditOpen"
        :project-id="project.id"
        mode="edit"
        :update="updateEditTarget"
        @close="updateEditOpen = false; updateEditTarget = null"
        @updated="onUpdateEdited"
      />
      <NewProjectModal
        :open="projectEditOpen"
        mode="edit"
        :project="project"
        @close="projectEditOpen = false"
        @updated="onProjectEdited"
      />
      <DeleteConfirmModal
        :open="projectDeleteOpen"
        :title="$t('project.deleteProject.title', { name: project.name })"
        :message="$t('project.deleteProject.message')"
        @confirm="confirmProjectDelete"
        @cancel="projectDeleteOpen = false"
      />
      <DeleteConfirmModal
        :open="updateDeleteOpen"
        :title="updateDeleteTarget ? $t('project.deleteUpdate.title', { title: updateDeleteTarget.title }) : undefined"
        :message="$t('project.deleteUpdate.message')"
        @confirm="confirmUpdateDelete"
        @cancel="updateDeleteOpen = false; updateDeleteTarget = null"
      />
      <NewQAModal
        :open="qaModalOpen"
        :projects="allProjects"
        :updates="updates"
        :members="members"
        :default-project-id="project.id"
        :default-update-id="qaDefaultUpdateId ?? updates[0]?.id"
        @close="qaModalOpen = false; qaDefaultUpdateId = undefined"
        @created="onQaCreated"
      />
    </template>
  </section>
</template>
