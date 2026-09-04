<script setup lang="ts">
import { Search, Inbox, X, ChevronDown, RefreshCw } from '@lucide/vue'
import StatusBadge from '~/components/base/StatusBadge.vue'
import PriorityBadge from '~/components/base/PriorityBadge.vue'
import AppSelect from '~/components/base/AppSelect.vue'
import { useSelectOptions } from '~/composables/useSelectOptions'
import { applyQaFilter, saveQaFilter, type QaFilterState } from '~/utils/qaFilter'
import type { Project, ProjectUpdate, QaItem } from '~/types/api'

const props = defineProps<{
  /** 전체 QA 목록(필터링 전). */
  items: QaItem[]
  /** 프로젝트/업데이트 필터 옵션. */
  projects: Project[]
  updates: ProjectUpdate[]
  /** 현재 보고 있는 QA id (하이라이트용). */
  currentId: number
  /** 목록에서 넘어온(또는 같은-업데이트 폴백) 초기 필터. 최초 1회만 반영. */
  initialFilter: QaFilterState
  /** 수동 새로고침 진행 중 여부 (아이콘 스핀 표시). */
  refreshing?: boolean
}>()

const emit = defineEmits<{
  /** 필터 결과의 ID 순서. 상세 페이지의 이전/다음 이동과 동기화. */
  (e: 'update:order', ids: number[]): void
  /** 목록 수동 새로고침 요청. 데이터 재조회는 상위(상세 페이지)가 담당. */
  (e: 'refresh'): void
  /** 프로젝트/업데이트 필터가 바뀌었다 — 상위가 그 범위의 목록을 받아 items 를 갱신한다. */
  (e: 'update:scope', scope: { projectId: string; updateId: string }): void
}>()

const auth = useAuthStore()
const router = useRouter()

const updateToProject = computed(() => {
  const m = new Map<number, number>()
  for (const u of props.updates) m.set(u.id, u.projectId)
  return m
})

// 사이드바에서 직접 조정 가능한 필터.
const projectId = ref(props.initialFilter.projectId)
const updateId = ref(props.initialFilter.updateId)
const status = ref(props.initialFilter.status)
const priority = ref(props.initialFilter.priority)

const { t } = useI18n()
const { qaStatus: qaStatusOpts, priority: priorityOpts } = useSelectOptions()
const projectFilterOptions = computed(() => [
  { value: 'all', label: t('qa.filter.allProjects') },
  ...props.projects.map((p) => ({ value: String(p.id), label: p.name })),
])
const statusFilterOptions = computed(() => [{ value: 'all', label: t('qa.filter.allStatuses') }, ...qaStatusOpts.value])
/** 긴급 → 낮음 순 */
const priorityFilterOptions = computed(() => [{ value: 'all', label: t('qa.filter.allPriorities') }, ...[...priorityOpts.value].reverse()])
const search = ref(props.initialFilter.search)

// 컨트롤로 노출하지 않는 나머지 필터(테스터/담당자/내것만)는 초기값을 그대로 적용하되,
// '전체 보기'로 한 번에 해제할 수 있게 둔다.
const extra = reactive({
  testerId: props.initialFilter.testerId,
  assigneeId: props.initialFilter.assigneeId,
  mineOnly: props.initialFilter.mineOnly,
})
const hasExtra = computed(() =>
  extra.testerId != null || extra.assigneeId != null || extra.mineOnly,
)

// 필터 접기/펴기 + 적용 중인 필터 개수(접었을 때 표시).
const filtersOpen = ref(true)
const activeCount = computed(() => {
  let n = 0
  if (search.value.trim()) n++
  if (projectId.value !== 'all') n++
  if (updateId.value !== 'all') n++
  if (status.value !== 'all') n++
  if (priority.value !== 'all') n++
  if (extra.testerId != null) n++
  if (extra.assigneeId != null) n++
  if (extra.mineOnly) n++
  if (hideReleased.value) n++
  return n
})
function clearExtra() {
  extra.testerId = null
  extra.assigneeId = null
  extra.mineOnly = false
}

/* '배포완료 숨기기' — QA 목록 필터와 공유하는 localStorage 영속 설정 */
const HIDE_RELEASED_KEY = 'qa-filter-hide-released'
const hideReleased = ref(false)
onMounted(() => {
  const v = localStorage.getItem(HIDE_RELEASED_KEY)
  if (v !== null) hideReleased.value = v === '1'
})
const releasedUpdateIds = computed(
  () => new Set(props.updates.filter((u) => u.status === 'released').map((u) => u.id)),
)
watch(hideReleased, (v) => {
  localStorage.setItem(HIDE_RELEASED_KEY, v ? '1' : '0')
  // 숨김을 켤 때 배포완료 업데이트가 선택돼 있으면 해제한다.
  if (v && updateId.value !== 'all' && releasedUpdateIds.value.has(Number(updateId.value))) {
    updateId.value = 'all'
  }
})

// 업데이트 옵션은 선택된 프로젝트로 캐스케이드 (+ 배포완료 숨기기 반영).
const updateOptions = computed(() => {
  const base = projectId.value === 'all'
    ? props.updates
    : props.updates.filter((u) => String(u.projectId) === projectId.value)
  return hideReleased.value ? base.filter((u) => u.status !== 'released') : base
})
const updateFilterOptions = computed(() => [
  { value: 'all', label: t('qa.filter.allUpdates') },
  ...updateOptions.value.map((u) => ({ value: String(u.id), label: `${u.version} - ${u.title}` })),
])
// 프로젝트를 바꾸면 그 프로젝트에 속하지 않는 업데이트 선택은 해제.
watch(projectId, (pid) => {
  if (pid === 'all' || updateId.value === 'all') return
  const u = props.updates.find((x) => String(x.id) === updateId.value)
  if (!u || String(u.projectId) !== pid) updateId.value = 'all'
})
// 범위(프로젝트/업데이트)가 바뀌면 상위가 그 범위의 목록을 다시 받는다. 위 watch 가 updateId 를 되돌리는 경우도 한 번에 반영되게 flush 'post'.
watch([projectId, updateId], ([p, u]) => emit('update:scope', { projectId: p, updateId: u }), { flush: 'post' })

const filterState = computed<QaFilterState>(() => ({
  status: status.value,
  priority: priority.value,
  projectId: projectId.value,
  updateId: updateId.value,
  search: search.value,
  testerId: extra.testerId,
  assigneeId: extra.assigneeId,
  mineOnly: extra.mineOnly,
}))

const filtered = computed(() =>
  applyQaFilter(
    props.items,
    filterState.value,
    auth.user?.id,
    updateToProject.value,
    hideReleased.value ? releasedUpdateIds.value : undefined,
  ),
)

// 필터 결과가 바뀔 때마다 이전/다음 이동용 순서를 상위로 전달.
watch(filtered, (list) => emit('update:order', list.map((q) => q.id)), { immediate: true })

function go(id: number) {
  if (id === props.currentId) return
  // 항목 간 이동 시에도 현재 필터 맥락을 유지(새로고침/직접진입 복원용).
  saveQaFilter(filterState.value)
  // replace 로 이동해 히스토리에 QA 항목들이 쌓이지 않게 한다.
  // → '뒤로' 가 거쳐온 QA 수만큼이 아니라 진입 직전(목록/프로젝트)으로 한 번에 돌아간다.
  router.replace(`/qa/${id}`)
}

/* ─── 선택 항목 자동 스크롤 ───
 * 목록(ul) 내부 scrollTop 만 조정한다. scrollIntoView 는 window 등 상위
 * 스크롤 컨테이너까지 움직일 수 있어 쓰지 않는다. */
const listRef = ref<HTMLUListElement | null>(null)
function scrollCurrentIntoView(center = false) {
  nextTick(() => {
    const list = listRef.value
    const el = list?.querySelector<HTMLElement>('[aria-current="true"]')
    if (!list || !el) return
    const elTop = el.offsetTop - list.offsetTop
    const elBottom = elTop + el.offsetHeight
    if (center) {
      list.scrollTop = elTop - (list.clientHeight - el.offsetHeight) / 2
    } else if (elTop < list.scrollTop) {
      list.scrollTop = elTop
    } else if (elBottom > list.scrollTop + list.clientHeight) {
      list.scrollTop = elBottom - list.clientHeight
    }
  })
}
// 최초 진입: 선택 항목을 목록 중앙 근처로. 이후 이전/다음 이동: 벗어났을 때만 최소 이동.
onMounted(() => scrollCurrentIntoView(true))
watch(() => props.currentId, () => scrollCurrentIntoView())
</script>

<template>
  <div class="flex max-h-[calc(100vh-3rem)] flex-col overflow-hidden rounded-xl border border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-900">
    <!-- 필터 -->
    <div class="shrink-0 border-b border-slate-100 dark:border-slate-800">
      <!-- 헤더(항상 표시): 접기 토글 + 적용 개수 + 새로고침 + 결과 건수 -->
      <div class="flex items-center justify-between pr-3">
        <button
          type="button"
          class="flex items-center gap-1.5 px-3 py-2.5 text-xs font-medium text-slate-600 dark:text-slate-300"
          :aria-expanded="filtersOpen"
          @click="filtersOpen = !filtersOpen"
        >
          <ChevronDown :class="['h-3.5 w-3.5 transition-transform', filtersOpen ? '' : '-rotate-90']" />
          {{ $t('qa.sidebar.filters') }}
          <span
            v-if="activeCount > 0"
            class="inline-flex h-4 min-w-[16px] items-center justify-center rounded-full bg-emerald-100 px-1 text-[10px] font-medium text-emerald-700 dark:bg-emerald-500/20 dark:text-emerald-300"
          >
            {{ activeCount }}
          </span>
        </button>
        <button
          type="button"
          :title="$t('qa.sidebar.refresh')"
          :disabled="refreshing"
          class="rounded p-1 text-slate-400 hover:bg-slate-50 hover:text-slate-600 disabled:opacity-60 dark:text-slate-500 dark:hover:bg-slate-800/60 dark:hover:text-slate-300"
          @click="emit('refresh')"
        >
          <RefreshCw :class="['h-3.5 w-3.5', refreshing ? 'animate-spin' : '']" />
        </button>
        <span class="ml-auto pl-2 text-[11px] text-slate-400 dark:text-slate-500">{{ $t('qa.sidebar.count', filtered.length) }}</span>
      </div>

      <!-- 컨트롤(접힘 가능) -->
      <div v-show="filtersOpen" class="space-y-2 px-3 pb-3">
      <div class="relative">
        <Search class="absolute left-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-slate-400 dark:text-slate-500" />
        <input
          v-model="search"
          type="text"
          :placeholder="$t('qa.sidebar.searchPlaceholder')"
          class="w-full rounded-lg border border-slate-200 py-1.5 pl-8 pr-2 text-xs focus:border-emerald-300 focus:outline-none focus:ring-2 focus:ring-emerald-200 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100 dark:placeholder-slate-500 dark:focus:ring-emerald-500/20"
        />
      </div>
      <AppSelect v-model="projectId" size="sm" rounded="lg" :options="projectFilterOptions" />
      <AppSelect v-model="updateId" size="sm" rounded="lg" :options="updateFilterOptions" />
      <label class="flex cursor-pointer select-none items-center gap-1.5 px-0.5 text-[11px] font-medium text-slate-500 dark:text-slate-400">
        <input
          v-model="hideReleased"
          type="checkbox"
          class="h-3.5 w-3.5 rounded border-slate-300 text-blue-500 focus:ring-blue-400 dark:border-slate-600 dark:bg-slate-900"
        />
        {{ $t('qa.filter.hideReleasedUpdates') }}
      </label>
      <div class="flex gap-2">
        <AppSelect v-model="status" class="min-w-0 flex-1" size="sm" rounded="lg" :options="statusFilterOptions" />
        <AppSelect v-model="priority" class="min-w-0 flex-1" size="sm" rounded="lg" :options="priorityFilterOptions" />
      </div>
        <div v-if="hasExtra" class="px-0.5">
          <button
            type="button"
            class="inline-flex items-center gap-1 text-[11px] text-emerald-600 hover:text-emerald-700 dark:text-emerald-400 dark:hover:text-emerald-300"
            @click="clearExtra"
          >
            <X class="h-3 w-3" /> {{ $t('qa.sidebar.clearExtraFilters') }}
          </button>
        </div>
      </div>
    </div>

    <!-- 목록 -->
    <ul ref="listRef" class="min-h-0 flex-1 overflow-y-auto">
      <li v-for="item in filtered" :key="item.id">
        <button
          type="button"
          :aria-current="item.id === currentId ? 'true' : undefined"
          :class="[
            'flex w-full flex-col gap-1.5 border-b border-slate-50 px-3 py-2.5 text-left transition dark:border-slate-800',
            item.id === currentId
              ? 'bg-emerald-50/70 ring-1 ring-inset ring-emerald-200 dark:bg-emerald-500/10 dark:ring-emerald-500/20'
              : 'hover:bg-slate-50 dark:hover:bg-slate-800/60',
          ]"
          @click="go(item.id)"
        >
          <p
            :class="[
              'line-clamp-2 text-xs font-medium',
              item.id === currentId ? 'text-emerald-900 dark:text-emerald-100' : 'text-slate-700 dark:text-slate-200',
            ]"
          >
            <span
              :class="[
                'mr-1 tabular-nums',
                item.id === currentId ? 'text-emerald-600 dark:text-emerald-400' : 'text-slate-400 dark:text-slate-500',
              ]"
            >#{{ item.id }}</span>{{ item.title }}
          </p>
          <div class="flex items-center gap-1.5">
            <StatusBadge :status="item.status" />
            <PriorityBadge :priority="item.priority" />
          </div>
        </button>
      </li>
      <li v-if="filtered.length === 0" class="px-3 py-10 text-center text-xs text-slate-400 dark:text-slate-500">
        <Inbox class="mx-auto mb-2 h-5 w-5 text-slate-300 dark:text-slate-600" />
        {{ $t('qa.filter.noMatch') }}
      </li>
    </ul>
  </div>
</template>
