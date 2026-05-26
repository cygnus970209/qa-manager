<script setup lang="ts">
import { FileText, Clock, Loader, CheckCheck, AlertTriangle, Plus } from '@lucide/vue'
import ProjectHeader from '~/components/feature/ProjectHeader.vue'
import UpdateAccordion from '~/components/feature/UpdateAccordion.vue'
import StatsCard from '~/components/feature/StatsCard.vue'
import NewUpdateModal from '~/components/feature/NewUpdateModal.vue'
import NewProjectModal from '~/components/feature/NewProjectModal.vue'
import NewQAModal from '~/components/feature/NewQAModal.vue'
import DeleteConfirmModal from '~/components/base/DeleteConfirmModal.vue'
import type { Member, Project, ProjectUpdate, QaItem } from '~/types/api'

const route = useRoute()
const router = useRouter()
const projectId = computed(() => Number(route.params.id))

const projectsApi = useProjects()
const updatesApi = useUpdates()
const qaApi = useQa()
const membersApi = useMembers()

const project = ref<Project | null>(null)
const updates = ref<ProjectUpdate[]>([])
const items = ref<QaItem[]>([])
const members = ref<Member[]>([])
const allProjects = ref<Project[]>([])
const loading = ref(true)
const error = ref<string | null>(null)

const updateModalOpen = ref(false)
const qaModalOpen = ref(false)
const qaDefaultUpdateId = ref<number | undefined>()

const projectEditOpen = ref(false)
const projectDeleteOpen = ref(false)

const updateEditOpen = ref(false)
const updateEditTarget = ref<ProjectUpdate | null>(null)
const updateDeleteOpen = ref(false)
const updateDeleteTarget = ref<ProjectUpdate | null>(null)

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
  } catch (e: any) {
    error.value = e?.data?.message ?? '프로젝트를 불러올 수 없습니다.'
  } finally {
    loading.value = false
  }
}

watch(projectId, () => { if (import.meta.client) load() })
if (import.meta.client) onMounted(load)

const stats = computed(() => {
  const t = items.value.length
  const resolved = items.value.filter((q) => q.status === 'resolved' || q.status === 'closed').length
  const inProgress = items.value.filter((q) => q.status === 'in_progress').length
  const pending = items.value.filter((q) => q.status === 'pending').length
  const critical = items.value.filter((q) => q.priority === 'critical').length
  return { total: t, resolved, inProgress, pending, critical }
})

const itemsByUpdate = computed(() => {
  const map = new Map<number, QaItem[]>()
  for (const q of items.value) {
    const arr = map.get(q.updateId) ?? []
    arr.push(q)
    map.set(q.updateId, arr)
  }
  return map
})

async function onProjectStatus(status: 'ACTIVE' | 'PAUSED' | 'COMPLETED') {
  if (!project.value) return
  project.value = await projectsApi.update(project.value.id, { status })
}
async function onUpdateStatus(updateId: number, status: 'IN_PROGRESS' | 'TESTING' | 'RELEASED') {
  const updated = await updatesApi.update(updateId, { status })
  updates.value = updates.value.map((u) => u.id === updateId ? updated : u)
}
async function onQaStatusChange(qaId: number, status: 'PENDING' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED') {
  const updated = await qaApi.update(qaId, { status })
  items.value = items.value.map((q) => (q.id === qaId ? updated : q))
}
function onAddInlineQa(updateId: number) {
  qaDefaultUpdateId.value = updateId
  qaModalOpen.value = true
}
function onUpdateCreated(u: ProjectUpdate) {
  updates.value = [u, ...updates.value]
}
function onQaCreated(q: QaItem) {
  items.value = [q, ...items.value]
}

function onProjectEdited(updated: Project) {
  project.value = updated
}
async function confirmProjectDelete() {
  if (!project.value) return
  await projectsApi.remove(project.value.id)
  projectDeleteOpen.value = false
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
      <div class="animate-pulse rounded-xl border border-slate-200 bg-white p-5">
        <div class="flex items-start justify-between">
          <div class="space-y-2">
            <div class="h-6 w-48 rounded bg-slate-200" />
            <div class="h-4 w-72 rounded bg-slate-100" />
            <div class="mt-2 flex gap-2">
              <div class="h-5 w-16 rounded-full bg-slate-100" />
              <div class="h-5 w-20 rounded-full bg-slate-100" />
            </div>
          </div>
          <div class="h-8 w-24 rounded-md bg-slate-100" />
        </div>
      </div>
      <!-- Stats skeleton -->
      <section class="mt-6 grid grid-cols-2 gap-3 md:grid-cols-3 md:gap-4 lg:grid-cols-5">
        <div v-for="i in 5" :key="i" class="h-[96px] animate-pulse rounded-xl border border-slate-200 bg-white p-5">
          <div class="flex items-start justify-between">
            <div class="space-y-2">
              <div class="h-3 w-16 rounded bg-slate-200" />
              <div class="h-7 w-10 rounded bg-slate-200" />
            </div>
            <div class="h-10 w-10 rounded-lg bg-slate-100" />
          </div>
        </div>
      </section>
      <!-- Updates skeleton -->
      <section class="mt-6 space-y-3">
        <div class="h-4 w-32 animate-pulse rounded bg-slate-200" />
        <div v-for="i in 3" :key="i" class="animate-pulse rounded-xl border border-slate-200 bg-white p-4">
          <div class="flex items-center justify-between">
            <div class="space-y-1.5">
              <div class="h-4 w-40 rounded bg-slate-200" />
              <div class="h-3 w-24 rounded bg-slate-100" />
            </div>
            <div class="h-6 w-20 rounded-full bg-slate-100" />
          </div>
        </div>
      </section>
    </template>
    <div v-else-if="error" class="rounded-md bg-red-50 px-4 py-3 text-sm text-red-700">
      {{ error }}
      <button class="ml-2 underline" @click="router.push('/')">대시보드로</button>
    </div>
    <template v-else-if="project">
      <ProjectHeader
        :project="project"
        :total-q-a="stats.total"
        :resolved-count="stats.resolved"
        :update-count="updates.length"
        @change-status="onProjectStatus"
        @edit="projectEditOpen = true"
        @remove="projectDeleteOpen = true"
      />

      <section class="mt-6 grid grid-cols-2 gap-3 md:grid-cols-3 md:gap-4 lg:grid-cols-5">
        <StatsCard title="전체 QA" :value="stats.total" :icon="FileText" icon-color="text-blue-500" icon-bg="bg-blue-50" />
        <StatsCard title="대기중" :value="stats.pending" :icon="Clock" icon-color="text-slate-500" icon-bg="bg-slate-100" />
        <StatsCard title="진행중" :value="stats.inProgress" :icon="Loader" icon-color="text-blue-500" icon-bg="bg-blue-50" />
        <StatsCard
          title="완료" :value="stats.resolved" :icon="CheckCheck"
          icon-color="text-emerald-500" icon-bg="bg-emerald-50"
          :trend="`완료율 ${stats.total > 0 ? Math.round((stats.resolved / stats.total) * 100) : 0}%`"
        />
        <StatsCard title="긴급" :value="stats.critical" :icon="AlertTriangle" icon-color="text-rose-500" icon-bg="bg-rose-50" />
      </section>

      <section class="mt-6">
        <div class="mb-3 flex items-center justify-between">
          <h2 class="text-sm font-semibold text-slate-700">업데이트별 QA 항목</h2>
          <div class="flex items-center gap-2 text-xs text-slate-400">
            <span>{{ updates.length }}개 업데이트 · {{ stats.total }}개 QA</span>
            <button
              type="button"
              class="inline-flex items-center gap-1 rounded-md bg-emerald-500 px-3 py-1.5 text-xs font-medium text-white hover:bg-emerald-600"
              @click="updateModalOpen = true"
            >
              <Plus class="h-3.5 w-3.5" /> 새 업데이트
            </button>
            <button
              type="button"
              class="inline-flex items-center gap-1 rounded-md bg-blue-500 px-3 py-1.5 text-xs font-medium text-white hover:bg-blue-600"
              @click="qaDefaultUpdateId = undefined; qaModalOpen = true"
            >
              <Plus class="h-3.5 w-3.5" /> 새 QA
            </button>
          </div>
        </div>

        <div v-if="updates.length === 0" class="rounded-lg border border-dashed border-slate-200 px-4 py-10 text-center text-sm text-slate-400">
          등록된 업데이트가 없습니다.
        </div>
        <div v-else class="flex flex-col gap-3">
          <UpdateAccordion
            v-for="u in updates"
            :key="u.id"
            :update="u"
            :items="itemsByUpdate.get(u.id) ?? []"
            :default-open="u === updates[0]"
            @change-status="onUpdateStatus"
            @add-qa="onAddInlineQa"
            @edit="openUpdateEdit"
            @remove="openUpdateDelete"
            @change-qa-status="onQaStatusChange"
          />
        </div>
      </section>

      <NewUpdateModal
        :open="updateModalOpen"
        :project-id="project.id"
        @close="updateModalOpen = false"
        @created="onUpdateCreated"
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
        :title="`'${project.name}' 프로젝트를 삭제하시겠습니까?`"
        message="프로젝트에 속한 업데이트와 QA 항목까지 영향이 갈 수 있습니다. 정말 삭제하시겠습니까?"
        @confirm="confirmProjectDelete"
        @cancel="projectDeleteOpen = false"
      />
      <DeleteConfirmModal
        :open="updateDeleteOpen"
        :title="updateDeleteTarget ? `'${updateDeleteTarget.title}' 업데이트를 삭제하시겠습니까?` : undefined"
        message="해당 업데이트에 속한 QA 항목까지 영향이 갈 수 있습니다. 정말 삭제하시겠습니까?"
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
