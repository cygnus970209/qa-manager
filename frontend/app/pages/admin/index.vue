<script setup lang="ts">
import {
  AlertTriangle,
  ArrowLeft,
  Bug,
  Edit3,
  Folder,
  KeyRound,
  Loader2,
  Send,
  Settings,
  Trash2,
  UserPlus,
  Users,
} from '@lucide/vue'
import DeleteConfirmModal from '~/components/base/DeleteConfirmModal.vue'
import MemberModal from '~/components/feature/MemberModal.vue'
import SettingsPanel from '~/components/feature/SettingsPanel.vue'
import StatsCard from '~/components/feature/StatsCard.vue'
import TeamsTestResultModal from '~/components/feature/TeamsTestResultModal.vue'
import type { Member, Project, ProjectStatus, ProjectUpdate, QaItem, QaPriority, QaStatus, QaStatusUpper, TeamsTestResult } from '~/types/api'

const router = useRouter()
const membersApi = useMembers()
const projectsApi = useProjects()
const qaApi = useQa()
const updatesApi = useUpdates()
const auth = useAuthStore()

type TabKey = 'projects' | 'qa' | 'members' | 'settings'
const route = useRoute()
const TAB_KEYS: TabKey[] = ['projects', 'qa', 'members', 'settings']
const initialTab = TAB_KEYS.includes(route.query.tab as TabKey) ? (route.query.tab as TabKey) : 'projects'
const activeTab = ref<TabKey>(initialTab)

const members = ref<Member[]>([])
const projects = ref<Project[]>([])
const qas = ref<QaItem[]>([])
const updates = ref<ProjectUpdate[]>([])
const loading = ref(true)

const memberModalOpen = ref(false)
const memberModalMode = ref<'create' | 'edit'>('create')
const editTarget = ref<Member | null>(null)

const deleteOpen = ref(false)
const deleteTarget = ref<Member | null>(null)

const teamsTestOpen = ref(false)
const teamsTestLoading = ref(false)
const teamsTestResult = ref<TeamsTestResult | null>(null)
const teamsTestTarget = ref<Member | null>(null)

async function runTeamsTest(m: Member) {
  teamsTestTarget.value = m
  teamsTestResult.value = null
  teamsTestLoading.value = true
  teamsTestOpen.value = true
  try {
    teamsTestResult.value = await membersApi.teamsTest(m.id)
  } catch (e: unknown) {
    teamsTestResult.value = {
      success: false,
      errorMessage: e instanceof Error ? e.message : '요청 실패',
      configOk: false,
      notifyEnabled: false,
      aadMapped: false,
      chatOk: false,
      sent: false,
    }
  } finally {
    teamsTestLoading.value = false
  }
}

async function loadAll() {
  loading.value = true
  try {
    const [m, p, q, u] = await Promise.all([
      membersApi.list(),
      projectsApi.list(),
      qaApi.list(),
      updatesApi.listAll(),
    ])
    members.value = m
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

function memberAssignedCount(memberId: number) {
  return qas.value.filter((q) =>
    q.assignee1?.id === memberId || q.assignee2?.id === memberId,
  ).length
}

const projectStatusConfig: Record<ProjectStatus, { label: string; color: string; bg: string }> = {
  active: { label: '진행중', color: 'text-emerald-600', bg: 'bg-emerald-50' },
  completed: { label: '완료', color: 'text-slate-500', bg: 'bg-slate-50' },
  paused: { label: '일시정지', color: 'text-amber-600', bg: 'bg-amber-50' },
}

const qaStatusConfig: Record<QaStatus, { label: string; color: string; bg: string }> = {
  needs_fix:     { label: '수정필요',     color: 'text-rose-600',    bg: 'bg-rose-50' },
  in_progress:   { label: '진행중',       color: 'text-blue-600',    bg: 'bg-blue-50' },
  fix_done:      { label: '수정완료',     color: 'text-amber-600',   bg: 'bg-amber-50' },
  confirmed:     { label: '확인완료',     color: 'text-emerald-600', bg: 'bg-emerald-50' },
  on_hold:       { label: '보류',         color: 'text-slate-600',   bg: 'bg-slate-100' },
  needs_recheck: { label: '추가확인필요', color: 'text-purple-600',  bg: 'bg-purple-50' },
}

const priorityConfig: Record<QaPriority, { label: string; color: string }> = {
  low: { label: '낮음', color: 'text-slate-500' },
  medium: { label: '중간', color: 'text-amber-500' },
  high: { label: '높음', color: 'text-orange-500' },
  critical: { label: '긴급', color: 'text-rose-500' },
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

function openCreate() {
  editTarget.value = null
  memberModalMode.value = 'create'
  memberModalOpen.value = true
}
function openEdit(m: Member) {
  editTarget.value = m
  memberModalMode.value = 'edit'
  memberModalOpen.value = true
}
function onCreated(m: Member) {
  members.value.push(m)
}
function onUpdated(m: Member) {
  members.value = members.value.map((x) => (x.id === m.id ? m : x))
}

function openDelete(m: Member) {
  if (m.id === auth.user?.id) {
    alert('본인 계정은 여기서 삭제할 수 없습니다.')
    return
  }
  deleteTarget.value = m
  deleteOpen.value = true
}
async function confirmDelete() {
  if (!deleteTarget.value) return
  await membersApi.remove(deleteTarget.value.id)
  members.value = members.value.filter((x) => x.id !== deleteTarget.value!.id)
  deleteOpen.value = false
  deleteTarget.value = null
}

async function resetPassword(m: Member) {
  if (!window.confirm(`${m.name}(${m.username})의 비밀번호를 "1234"로 초기화할까요?`)) return
  try {
    await membersApi.resetPassword(m.id)
    window.alert(`${m.name}의 비밀번호가 "1234"로 초기화되었습니다.`)
  } catch (e: unknown) {
    window.alert(e instanceof Error ? e.message : '비밀번호 초기화 실패')
  }
}
</script>

<template>
  <section>
    <!-- Header -->
    <header class="mb-6 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between md:mb-8">
      <div>
        <h1 class="text-xl font-bold text-slate-800 md:text-2xl">관리자 페이지</h1>
        <p class="mt-1 text-sm text-slate-500">프로젝트, QA 항목, 팀원을 관리하세요</p>
      </div>
      <button
        type="button"
        class="inline-flex items-center gap-2 whitespace-nowrap rounded-lg border border-slate-200 bg-white px-4 py-2 text-sm font-medium text-slate-600 hover:bg-slate-50"
        @click="router.push('/')"
      >
        <ArrowLeft class="h-4 w-4" />
        대시보드로
      </button>
    </header>

    <!-- Stats -->
    <div class="mb-6 grid grid-cols-2 gap-3 md:mb-8 md:grid-cols-4 md:gap-4">
      <template v-if="loading">
        <div v-for="i in 4" :key="i" class="h-[96px] animate-pulse rounded-xl border border-slate-200 bg-white p-5">
          <div class="flex items-start justify-between">
            <div class="space-y-2">
              <div class="h-3 w-20 rounded bg-slate-200" />
              <div class="h-7 w-12 rounded bg-slate-200" />
              <div class="h-3 w-16 rounded bg-slate-100" />
            </div>
            <div class="h-10 w-10 rounded-lg bg-slate-100" />
          </div>
        </div>
      </template>
      <template v-else>
        <StatsCard
          title="전체 프로젝트"
          :value="stats.totalProjects"
          :icon="Folder"
          icon-color="text-emerald-500"
          icon-bg="bg-emerald-50"
          :trend="`진행중 ${stats.activeProjects}개`"
        />
        <StatsCard
          title="전체 QA"
          :value="stats.totalQA"
          :icon="Bug"
          icon-color="text-rose-500"
          icon-bg="bg-rose-50"
        />
        <StatsCard
          title="진행중 프로젝트"
          :value="stats.activeProjects"
          :icon="Loader2"
          icon-color="text-emerald-500"
          icon-bg="bg-emerald-50"
        />
        <StatsCard
          title="긴급 QA"
          :value="stats.criticalQA"
          :icon="AlertTriangle"
          icon-color="text-red-500"
          icon-bg="bg-red-50"
        />
      </template>
    </div>

    <!-- Tabs -->
    <div class="mb-4 flex items-center justify-between">
      <div class="flex w-fit items-center gap-1 rounded-lg bg-slate-100 p-1">
        <button
          v-for="tab in [
            { key: 'projects' as TabKey, label: '프로젝트 관리', icon: Folder },
            { key: 'qa' as TabKey, label: 'QA 관리', icon: Bug },
            { key: 'members' as TabKey, label: '팀원 관리', icon: Users },
            { key: 'settings' as TabKey, label: '설정', icon: Settings },
          ]"
          :key="tab.key"
          type="button"
          :class="[
            'flex items-center gap-1.5 rounded-md px-4 py-2 text-sm font-medium transition-colors whitespace-nowrap',
            activeTab === tab.key ? 'bg-white text-slate-800 shadow-sm' : 'text-slate-500 hover:text-slate-700',
          ]"
          @click="activeTab = tab.key"
        >
          <component :is="tab.icon" class="h-4 w-4" />
          {{ tab.label }}
        </button>
      </div>

      <button
        v-if="activeTab === 'members'"
        type="button"
        class="inline-flex items-center gap-1.5 whitespace-nowrap rounded-lg bg-emerald-600 px-3 py-2 text-sm font-medium text-white hover:bg-emerald-700"
        @click="openCreate"
      >
        <UserPlus class="h-4 w-4" />
        팀원 추가
      </button>
    </div>

    <!-- Content -->
    <div class="overflow-hidden rounded-xl border border-slate-200 bg-white">
      <div v-if="loading" class="overflow-x-auto">
        <table class="w-full">
          <thead class="border-b border-slate-200 bg-slate-50">
            <tr>
              <th v-for="i in 5" :key="i" class="px-4 py-3"><div class="h-3 w-16 animate-pulse rounded bg-slate-200" /></th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-100">
            <tr v-for="r in 6" :key="r">
              <td v-for="c in 5" :key="c" class="px-4 py-4"><div class="h-4 animate-pulse rounded bg-slate-100" :style="{ width: `${40 + ((r * c) % 5) * 15}%` }" /></td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Projects -->
      <div v-else-if="activeTab === 'projects'" class="overflow-x-auto">
        <table class="w-full text-left text-sm">
          <thead class="border-b border-slate-200 bg-slate-50">
            <tr>
              <th class="px-4 py-3 text-xs font-semibold text-slate-500">프로젝트명</th>
              <th class="w-36 px-4 py-3 text-xs font-semibold text-slate-500">상태</th>
              <th class="w-24 px-4 py-3 text-xs font-semibold text-slate-500">QA 수</th>
              <th class="w-32 px-4 py-3 text-xs font-semibold text-slate-500">생성일</th>
              <th class="w-24 px-4 py-3 text-xs font-semibold text-slate-500">동작</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-100">
            <tr v-for="p in projects" :key="p.id" class="transition-colors hover:bg-slate-50">
              <td class="px-4 py-3 font-medium text-slate-800">{{ p.name }}</td>
              <td class="px-4 py-3">
                <select
                  :value="p.status"
                  :class="['cursor-pointer rounded-full border-0 px-2 py-1 text-xs font-medium outline-none', projectStatusConfig[p.status].bg, projectStatusConfig[p.status].color]"
                  @change="changeProjectStatus(p, ($event.target as HTMLSelectElement).value as ProjectStatus)"
                >
                  <option value="active">{{ projectStatusConfig.active.label }}</option>
                  <option value="paused">{{ projectStatusConfig.paused.label }}</option>
                  <option value="completed">{{ projectStatusConfig.completed.label }}</option>
                </select>
              </td>
              <td class="px-4 py-3 text-slate-600">{{ projectQaCount(p.id) }}</td>
              <td class="px-4 py-3 text-slate-500">{{ p.createdAt ? p.createdAt.slice(0, 10) : '-' }}</td>
              <td class="px-4 py-3">
                <button
                  type="button"
                  class="text-xs font-medium text-emerald-500 hover:text-emerald-600"
                  @click="router.push(`/project/${p.id}`)"
                >상세보기 →</button>
              </td>
            </tr>
            <tr v-if="projects.length === 0">
              <td colspan="5" class="px-4 py-8 text-center text-sm text-slate-400">프로젝트가 없습니다.</td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- QA -->
      <div v-else-if="activeTab === 'qa'" class="overflow-x-auto">
        <table class="w-full text-left text-sm">
          <thead class="border-b border-slate-200 bg-slate-50">
            <tr>
              <th class="px-4 py-3 text-xs font-semibold text-slate-500">제목</th>
              <th class="w-40 px-4 py-3 text-xs font-semibold text-slate-500">프로젝트</th>
              <th class="w-28 px-4 py-3 text-xs font-semibold text-slate-500">우선순위</th>
              <th class="w-32 px-4 py-3 text-xs font-semibold text-slate-500">상태</th>
              <th class="w-24 px-4 py-3 text-xs font-semibold text-slate-500">담당자</th>
              <th class="w-24 px-4 py-3 text-xs font-semibold text-slate-500">동작</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-100">
            <tr v-for="q in qas" :key="q.id" class="transition-colors hover:bg-slate-50">
              <td class="max-w-xs truncate px-4 py-3 font-medium text-slate-800">{{ q.title }}</td>
              <td class="px-4 py-3 text-slate-600">{{ projectName(q.updateId) }}</td>
              <td class="px-4 py-3">
                <span :class="['text-xs font-semibold', priorityConfig[q.priority].color]">{{ priorityConfig[q.priority].label }}</span>
              </td>
              <td class="px-4 py-3">
                <select
                  :value="q.status"
                  :class="['cursor-pointer rounded-full border-0 px-2 py-1 text-xs font-medium outline-none', qaStatusConfig[q.status].bg, qaStatusConfig[q.status].color]"
                  @change="changeQaStatus(q, ($event.target as HTMLSelectElement).value as QaStatus)"
                >
                  <option value="needs_fix">{{ qaStatusConfig.needs_fix.label }}</option>
                  <option value="in_progress">{{ qaStatusConfig.in_progress.label }}</option>
                  <option value="fix_done">{{ qaStatusConfig.fix_done.label }}</option>
                  <option value="confirmed">{{ qaStatusConfig.confirmed.label }}</option>
                  <option value="on_hold">{{ qaStatusConfig.on_hold.label }}</option>
                  <option value="needs_recheck">{{ qaStatusConfig.needs_recheck.label }}</option>
                </select>
              </td>
              <td class="px-4 py-3 text-xs text-slate-600">
                <div class="flex flex-col gap-0.5">
                  <span v-if="q.tester" class="text-slate-500">T: {{ q.tester.name }}</span>
                  <span v-if="q.assignee1 || q.assignee2">
                    {{ [q.assignee1?.name, q.assignee2?.name].filter(Boolean).join(', ') }}
                  </span>
                  <span v-if="!q.tester && !q.assignee1 && !q.assignee2" class="text-slate-400">-</span>
                </div>
              </td>
              <td class="px-4 py-3">
                <button
                  type="button"
                  class="text-xs font-medium text-emerald-500 hover:text-emerald-600"
                  @click="router.push(`/qa/${q.id}`)"
                >상세보기 →</button>
              </td>
            </tr>
            <tr v-if="qas.length === 0">
              <td colspan="6" class="px-4 py-8 text-center text-sm text-slate-400">QA 항목이 없습니다.</td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Members -->
      <div v-else-if="activeTab === 'members'" class="overflow-x-auto">
        <table class="w-full text-left text-sm">
          <thead class="border-b border-slate-200 bg-slate-50">
            <tr>
              <th class="w-12 px-4 py-3 text-xs font-semibold text-slate-500">#</th>
              <th class="px-4 py-3 text-xs font-semibold text-slate-500">이름</th>
              <th class="w-32 px-4 py-3 text-xs font-semibold text-slate-500">아이디</th>
              <th class="w-40 px-4 py-3 text-xs font-semibold text-slate-500">역할</th>
              <th class="w-24 px-4 py-3 text-xs font-semibold text-slate-500">QA 할당</th>
              <th class="w-24 px-4 py-3 text-xs font-semibold text-slate-500">동작</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-100">
            <tr v-for="(m, index) in members" :key="m.id" class="transition-colors hover:bg-slate-50">
              <td class="px-4 py-3 text-slate-400">{{ index + 1 }}</td>
              <td class="px-4 py-3">
                <div class="flex items-center gap-3">
                  <img
                    v-if="m.avatarUrl"
                    :src="m.avatarUrl"
                    :alt="m.name"
                    class="h-8 w-8 rounded-full bg-slate-100 object-cover"
                  />
                  <div v-else class="flex h-8 w-8 items-center justify-center rounded-full bg-emerald-100 text-sm font-medium text-emerald-600">{{ m.name.charAt(0) }}</div>
                  <span class="font-medium text-slate-800">{{ m.name }}</span>
                </div>
              </td>
              <td class="px-4 py-3 text-xs text-slate-500">{{ m.username }}</td>
              <td class="px-4 py-3 text-slate-600">{{ m.role ?? '-' }}</td>
              <td class="px-4 py-3">
                <span class="rounded-full bg-slate-100 px-2 py-1 text-xs font-medium text-slate-600">
                  {{ memberAssignedCount(m.id) }}건
                </span>
              </td>
              <td class="px-4 py-3">
                <div class="flex items-center gap-2">
                  <button
                    type="button"
                    class="flex h-8 w-8 items-center justify-center rounded-lg text-slate-500 transition-colors hover:bg-sky-50 hover:text-sky-500"
                    title="Teams 발송 테스트"
                    @click="runTeamsTest(m)"
                  >
                    <Send class="h-4 w-4" />
                  </button>
                  <button
                    type="button"
                    class="flex h-8 w-8 items-center justify-center rounded-lg text-slate-500 transition-colors hover:bg-amber-50 hover:text-amber-500"
                    title="비밀번호 1234로 초기화"
                    @click="resetPassword(m)"
                  >
                    <KeyRound class="h-4 w-4" />
                  </button>
                  <button
                    type="button"
                    class="flex h-8 w-8 items-center justify-center rounded-lg text-slate-500 transition-colors hover:bg-emerald-50 hover:text-emerald-500"
                    title="수정"
                    @click="openEdit(m)"
                  >
                    <Edit3 class="h-4 w-4" />
                  </button>
                  <button
                    type="button"
                    class="flex h-8 w-8 items-center justify-center rounded-lg text-slate-500 transition-colors hover:bg-red-50 hover:text-red-500"
                    title="삭제"
                    @click="openDelete(m)"
                  >
                    <Trash2 class="h-4 w-4" />
                  </button>
                </div>
              </td>
            </tr>
            <tr v-if="members.length === 0">
              <td colspan="6" class="px-4 py-8 text-center text-sm text-slate-400">팀원이 없습니다.</td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Settings -->
      <SettingsPanel v-else-if="activeTab === 'settings'" />
    </div>

    <MemberModal
      :open="memberModalOpen"
      :mode="memberModalMode"
      :member="editTarget"
      @close="memberModalOpen = false"
      @created="onCreated"
      @updated="onUpdated"
    />

    <DeleteConfirmModal
      :open="deleteOpen"
      :title="deleteTarget ? `'${deleteTarget.name}' 팀원을 삭제하시겠습니까?` : undefined"
      message="삭제하면 복구할 수 없습니다. 계속하시겠습니까?"
      @confirm="confirmDelete"
      @cancel="deleteOpen = false; deleteTarget = null"
    />

    <TeamsTestResultModal
      :open="teamsTestOpen"
      :loading="teamsTestLoading"
      :result="teamsTestResult"
      :member-name="teamsTestTarget?.name"
      @close="teamsTestOpen = false"
    />
  </section>
</template>
