<script setup lang="ts">
import { ArrowLeft, ChevronLeft, ChevronRight } from '@lucide/vue'
import QAInfoPanel from '~/components/feature/QAInfoPanel.vue'
import QACommentSection from '~/components/feature/QACommentSection.vue'
import QAHistoryList from '~/components/feature/QAHistoryList.vue'
import QANavSidebar from '~/components/feature/QANavSidebar.vue'
import NewQAModal from '~/components/feature/NewQAModal.vue'
import { applyQaFilter, emptyQaFilter, loadQaFilter, type QaFilterState } from '~/utils/qaFilter'
import type { Member, Project, ProjectUpdate, QaComment, QaHistoryEntry, QaItem } from '~/types/api'

// 고정 key: 항목 간 이동(/qa/1 → /qa/2)에서 페이지를 리마운트하지 않고 재사용한다.
// (Nuxt 기본 key 는 파라미터 치환 경로라 이동마다 리마운트 → 사이드바 스크롤/목록이 초기화됨.
//  아래 watch(qaId) + '최초 1회만 로드' 가드가 재사용 전제의 갱신 로직.)
definePageMeta({ key: 'qa-detail' })

const route = useRoute()
const router = useRouter()
const qaId = computed(() => Number(route.params.id))

const qaApi = useQa()
const membersApi = useMembers()
const projectsApi = useProjects()
const updatesApi = useUpdates()
const auth = useAuthStore()

const item = ref<QaItem | null>(null)
const history = ref<QaHistoryEntry[]>([])
const comments = ref<QaComment[]>([])
const members = ref<Member[]>([])
const loading = ref(true)
const error = ref<string | null>(null)

// 사이드바(마스터-디테일)용 전체 QA 목록 + 프로젝트/업데이트 옵션 + 초기 필터.
const allItems = ref<QaItem[]>([])
const sideProjects = ref<Project[]>([])
const sideUpdates = ref<ProjectUpdate[]>([])
const initialFilter = ref<QaFilterState | null>(null)

const updateToProject = computed(() => {
  const m = new Map<number, number>()
  for (const u of sideUpdates.value) m.set(u.id, u.projectId)
  return m
})

// 현재 QA가 이동 가능한 업데이트 목록(같은 프로젝트의 버전들). 버전 오름차순 정렬.
const movableUpdates = computed(() => {
  if (!item.value) return []
  const pid = updateToProject.value.get(item.value.updateId)
  const list = pid == null ? sideUpdates.value : sideUpdates.value.filter((u) => u.projectId === pid)
  return [...list].sort((a, b) => a.version.localeCompare(b.version, undefined, { numeric: true }))
})

/* ─── 좌/우 사이드바 접기/펴기 (localStorage 영속) ─── */
const LEFT_KEY = 'qa-detail-left-open'
const RIGHT_KEY = 'qa-detail-right-open'
const leftOpen = ref(true)
const rightOpen = ref(true)
onMounted(() => {
  const l = localStorage.getItem(LEFT_KEY)
  const r = localStorage.getItem(RIGHT_KEY)
  if (l !== null) leftOpen.value = l === '1'
  if (r !== null) rightOpen.value = r === '1'
})
watch(leftOpen, (v) => localStorage.setItem(LEFT_KEY, v ? '1' : '0'))
watch(rightOpen, (v) => localStorage.setItem(RIGHT_KEY, v ? '1' : '0'))

/**
 * 사이드바 초기 필터 결정.
 * - 목록에서 저장된 필터가 있고, 그 결과에 현재 항목이 포함되면 그대로 사용.
 * - 아니면(직접 URL 진입 / 오래된 필터) 현재 항목의 프로젝트·업데이트로 스코프해 폴백.
 */
function resolveInitialFilter(all: QaItem[], current: QaItem): QaFilterState {
  const saved = loadQaFilter()
  if (saved && applyQaFilter(all, saved, auth.user?.id, updateToProject.value).some((q) => q.id === current.id)) {
    return saved
  }
  const pid = updateToProject.value.get(current.updateId)
  return {
    ...emptyQaFilter(),
    projectId: pid != null ? String(pid) : 'all',
    updateId: String(current.updateId),
  }
}

async function load() {
  loading.value = true
  error.value = null
  try {
    item.value = await qaApi.get(qaId.value)
    history.value = await qaApi.history(qaId.value)
    comments.value = await qaApi.listComments(qaId.value)
    members.value = await membersApi.list()
    // 사이드바 목록/옵션은 최초 1회만 로드(이후 항목 이동 시 재요청하지 않음).
    if (allItems.value.length === 0) {
      allItems.value = await qaApi.list()
    }
    if (sideProjects.value.length === 0) {
      sideProjects.value = await projectsApi.list()
    }
    if (sideUpdates.value.length === 0) {
      sideUpdates.value = await updatesApi.listAll()
    }
    // 초기 필터도 최초 1회만 산출(이동/필터 조정 시 사이드바 상태를 초기화하지 않기 위함).
    if (initialFilter.value === null && item.value) {
      initialFilter.value = resolveInitialFilter(allItems.value, item.value)
    }
  } catch (e: any) {
    error.value = e?.data?.message ?? 'QA 항목을 불러올 수 없습니다.'
  } finally {
    loading.value = false
  }
}
if (import.meta.client) onMounted(load)
watch(qaId, () => { if (import.meta.client) load() })

/* ─── 사이드바 수동 새로고침 ───
 * 페이지 재사용(고정 key)으로 목록은 최초 1회만 로드되므로, 최신화는 이 버튼으로 한다. */
const sideRefreshing = ref(false)
async function refreshSidebar() {
  if (sideRefreshing.value) return
  sideRefreshing.value = true
  try {
    const [items, projects, updates] = await Promise.all([
      qaApi.list(), projectsApi.list(), updatesApi.listAll(),
    ])
    allItems.value = items
    sideProjects.value = projects
    sideUpdates.value = updates
  } catch (e) {
    console.error('sidebar refresh failed', e)
  } finally {
    sideRefreshing.value = false
  }
}

/* ─── 상세에서 바로 새 QA 등록 ─── */
const newQaOpen = ref(false)
function onQaCreated(q: QaItem) {
  // 사이드바 목록에 즉시 반영 (필터에 따라 노출 여부는 달라질 수 있음).
  allItems.value = [q, ...allItems.value]
}

async function onUpdated(next: QaItem) {
  item.value = next
  history.value = await qaApi.history(qaId.value)
  // 사이드바 목록에도 변경(상태/제목 등)을 즉시 반영.
  allItems.value = allItems.value.map((q) => (q.id === next.id ? next : q))
}

function onRemoved() {
  allItems.value = allItems.value.filter((q) => q.id !== qaId.value)
  router.push('/')
}

/* ─── 이전/다음 게시글 (사이드바 필터 결과와 동기화) ───
 * 사이드바가 emit 하는 현재 필터 결과의 ID 순서를 기준으로 앞/뒤 항목으로 이동한다.
 * (사이드바가 숨겨지는 모바일에서도 컴포넌트는 마운트돼 순서를 전달하므로 동작한다.)
 */
const navIds = ref<number[]>([])
const currentIdx = computed(() => navIds.value.indexOf(qaId.value))
const hasPrev = computed(() => currentIdx.value > 0)
const hasNext = computed(() => currentIdx.value >= 0 && currentIdx.value < navIds.value.length - 1)

function goPrev() {
  if (!hasPrev.value) return
  // replace: 상세 내 이동은 히스토리에 쌓지 않아 '뒤로' 가 진입 직전으로 한 번에 돌아가게 한다.
  router.replace(`/qa/${navIds.value[currentIdx.value - 1]}`)
}
function goNext() {
  if (!hasNext.value) return
  router.replace(`/qa/${navIds.value[currentIdx.value + 1]}`)
}
</script>

<template>
  <section>
    <div class="mb-3 flex items-center justify-between">
      <button class="inline-flex items-center gap-1 text-xs text-slate-500 hover:text-slate-900" type="button" @click="router.back()">
        <ArrowLeft class="h-3.5 w-3.5" /> 뒤로
      </button>
      <div v-if="navIds.length > 0 && currentIdx >= 0" class="flex items-center gap-1 text-xs">
        <span class="mr-1 text-slate-400 tabular-nums">{{ currentIdx + 1 }} / {{ navIds.length }}</span>
        <button
          type="button"
          :disabled="!hasPrev"
          class="inline-flex items-center gap-1 rounded-md border border-slate-200 px-2 py-1 text-slate-600 hover:bg-slate-50 disabled:opacity-40"
          @click="goPrev"
        >
          <ChevronLeft class="h-3.5 w-3.5" /> 이전
        </button>
        <button
          type="button"
          :disabled="!hasNext"
          class="inline-flex items-center gap-1 rounded-md border border-slate-200 px-2 py-1 text-slate-600 hover:bg-slate-50 disabled:opacity-40"
          @click="goNext"
        >
          다음 <ChevronRight class="h-3.5 w-3.5" />
        </button>
      </div>
    </div>

    <div class="flex gap-6">
      <!-- 좌측 목록 사이드바(마스터-디테일): lg 이상에서만 노출. v-show 로 접어도
           컴포넌트는 마운트 유지 → 이전/다음 이동용 순서(@update:order)는 계속 전달된다. -->
      <aside v-show="leftOpen" class="relative hidden w-72 shrink-0 lg:block">
        <div class="sticky top-20">
          <!-- 박스 우측 가장자리에 붙은 접기 핸들 -->
          <button
            type="button"
            title="목록 접기"
            class="absolute -right-3 top-3 z-10 hidden h-6 w-6 items-center justify-center rounded-full border border-slate-200 bg-white text-slate-500 shadow-sm hover:bg-slate-50 hover:text-slate-700 lg:flex"
            @click="leftOpen = false"
          >
            <ChevronLeft class="h-3.5 w-3.5" />
          </button>
          <div v-if="allItems.length > 0 && initialFilter">
            <QANavSidebar
              :items="allItems"
              :projects="sideProjects"
              :updates="sideUpdates"
              :current-id="qaId"
              :initial-filter="initialFilter"
              :refreshing="sideRefreshing"
              @update:order="navIds = $event"
              @refresh="refreshSidebar"
            />
          </div>
        </div>
      </aside>
      <!-- 접힘 상태: 그 자리에 얇은 펼치기 핸들 -->
      <button
        v-show="!leftOpen"
        type="button"
        title="목록 펼치기"
        class="sticky top-20 hidden h-16 shrink-0 flex-col items-center justify-center gap-1 self-start rounded-md border border-slate-200 bg-white px-1.5 text-slate-500 shadow-sm hover:bg-slate-50 hover:text-slate-700 lg:flex"
        @click="leftOpen = true"
      >
        <ChevronRight class="h-3.5 w-3.5" />
        <span class="text-[10px] [writing-mode:vertical-rl]">목록</span>
      </button>

      <div class="min-w-0 flex-1">
    <div v-if="loading" class="grid grid-cols-1 gap-6 lg:grid-cols-3">
      <div class="space-y-6 lg:col-span-2">
        <!-- QA Info Panel skeleton -->
        <div class="animate-pulse space-y-4 rounded-xl border border-slate-200 bg-white p-5">
          <div class="flex items-start justify-between">
            <div class="flex-1 space-y-2">
              <div class="h-5 w-3/4 rounded bg-slate-200" />
              <div class="flex gap-2">
                <div class="h-5 w-16 rounded-full bg-slate-100" />
                <div class="h-5 w-14 rounded-full bg-slate-100" />
              </div>
            </div>
            <div class="flex gap-2">
              <div class="h-7 w-7 rounded-md bg-slate-100" />
              <div class="h-7 w-7 rounded-md bg-slate-100" />
            </div>
          </div>
          <div class="space-y-2">
            <div class="h-3 w-full rounded bg-slate-100" />
            <div class="h-3 w-5/6 rounded bg-slate-100" />
            <div class="h-3 w-2/3 rounded bg-slate-100" />
          </div>
          <div class="grid grid-cols-2 gap-3 pt-2">
            <div class="h-12 rounded-lg bg-slate-100" />
            <div class="h-12 rounded-lg bg-slate-100" />
          </div>
        </div>
        <!-- Comment skeleton -->
        <div class="overflow-hidden rounded-xl border border-slate-200 bg-white">
          <div class="border-b border-slate-100 p-4 md:p-5">
            <div class="h-4 w-24 animate-pulse rounded bg-slate-200" />
          </div>
          <div class="space-y-5 p-4 md:p-5">
            <div v-for="i in 3" :key="i" class="flex animate-pulse gap-3">
              <div class="h-8 w-8 shrink-0 rounded-full bg-slate-200" />
              <div class="flex-1 space-y-2">
                <div class="h-3 w-32 rounded bg-slate-200" />
                <div class="h-3 w-full rounded bg-slate-100" />
                <div class="h-3 w-2/3 rounded bg-slate-100" />
              </div>
            </div>
          </div>
        </div>
      </div>
      <div>
        <!-- History skeleton -->
        <div class="animate-pulse space-y-3 rounded-xl border border-slate-200 bg-white p-5">
          <div class="h-4 w-24 rounded bg-slate-200" />
          <div v-for="i in 4" :key="i" class="space-y-1.5 border-l-2 border-slate-100 pl-3">
            <div class="h-3 w-3/4 rounded bg-slate-100" />
            <div class="h-3 w-1/3 rounded bg-slate-100" />
          </div>
        </div>
      </div>
    </div>
    <div v-else-if="error" class="rounded-md bg-red-50 px-4 py-3 text-sm text-red-700">{{ error }}</div>
    <template v-else-if="item">
      <div class="flex flex-col gap-6 lg:flex-row">
        <div class="min-w-0 flex-1 space-y-6">
          <QAInfoPanel :item="item" :members="members" :updates="movableUpdates" @updated="onUpdated" @removed="onRemoved" @add-qa="newQaOpen = true" />
          <QACommentSection
            :qa-item-id="item.id"
            :comments="comments"
            :members="members"
            :qa-items="allItems"
            @refreshed="comments = $event"
          />
        </div>
        <!-- 접힘 상태: 본문 우측에 얇은 펼치기 핸들 -->
        <button
          v-show="!rightOpen"
          type="button"
          title="이력 펼치기"
          class="hidden h-16 shrink-0 flex-col items-center justify-center gap-1 self-start rounded-md border border-slate-200 bg-white px-1.5 text-slate-500 shadow-sm hover:bg-slate-50 hover:text-slate-700 lg:flex lg:sticky lg:top-20"
          @click="rightOpen = true"
        >
          <ChevronLeft class="h-3.5 w-3.5" />
          <span class="text-[10px] [writing-mode:vertical-rl]">이력</span>
        </button>
        <aside v-show="rightOpen" class="relative w-full shrink-0 lg:w-80">
          <div class="lg:sticky lg:top-20">
            <!-- 박스 좌측 가장자리에 붙은 접기 핸들 -->
            <button
              type="button"
              title="이력 접기"
              class="absolute -left-3 top-3 z-10 hidden h-6 w-6 items-center justify-center rounded-full border border-slate-200 bg-white text-slate-500 shadow-sm hover:bg-slate-50 hover:text-slate-700 lg:flex"
              @click="rightOpen = false"
            >
              <ChevronRight class="h-3.5 w-3.5" />
            </button>
            <QAHistoryList :entries="history" />
          </div>
        </aside>
      </div>

      <NewQAModal
        :open="newQaOpen"
        :projects="sideProjects"
        :updates="sideUpdates"
        :members="members"
        :default-update-id="item.updateId"
        @close="newQaOpen = false"
        @created="onQaCreated"
      />
    </template>
      </div>
    </div>
  </section>
</template>
