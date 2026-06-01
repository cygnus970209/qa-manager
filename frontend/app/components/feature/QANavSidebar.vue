<script setup lang="ts">
import { Search, Inbox, X } from '@lucide/vue'
import StatusBadge from '~/components/base/StatusBadge.vue'
import PriorityBadge from '~/components/base/PriorityBadge.vue'
import { applyQaFilter, saveQaFilter, type QaFilterState } from '~/utils/qaFilter'
import type { QaItem } from '~/types/api'

const props = defineProps<{
  /** 전체 QA 목록(필터링 전). */
  items: QaItem[]
  /** 현재 보고 있는 QA id (하이라이트용). */
  currentId: number
  /** 목록에서 넘어온(또는 같은-업데이트 폴백) 초기 필터. 최초 1회만 반영. */
  initialFilter: QaFilterState
}>()

const emit = defineEmits<{
  /** 필터 결과의 ID 순서. 상세 페이지의 이전/다음 이동과 동기화. */
  (e: 'update:order', ids: number[]): void
}>()

const auth = useAuthStore()
const router = useRouter()

// 사이드바에서 직접 조정 가능한 필터(정적 옵션이라 추가 조회 불필요).
const status = ref(props.initialFilter.status)
const priority = ref(props.initialFilter.priority)
const search = ref(props.initialFilter.search)

// 컨트롤로 노출하지 않는 나머지 필터(테스터/담당자/업데이트/내것만)는
// 초기값을 그대로 적용하되, '전체 보기'로 한 번에 해제할 수 있게 둔다.
const extra = reactive({
  updateId: props.initialFilter.updateId,
  testerId: props.initialFilter.testerId,
  assigneeId: props.initialFilter.assigneeId,
  mineOnly: props.initialFilter.mineOnly,
})
const hasExtra = computed(() =>
  extra.updateId !== 'all'
  || extra.testerId != null
  || extra.assigneeId != null
  || extra.mineOnly,
)
function clearExtra() {
  extra.updateId = 'all'
  extra.testerId = null
  extra.assigneeId = null
  extra.mineOnly = false
}

const filterState = computed<QaFilterState>(() => ({
  status: status.value,
  priority: priority.value,
  search: search.value,
  updateId: extra.updateId,
  testerId: extra.testerId,
  assigneeId: extra.assigneeId,
  mineOnly: extra.mineOnly,
}))

const filtered = computed(() => applyQaFilter(props.items, filterState.value, auth.user?.id))

// 필터 결과가 바뀔 때마다 이전/다음 이동용 순서를 상위로 전달.
watch(filtered, (list) => emit('update:order', list.map((q) => q.id)), { immediate: true })

function go(id: number) {
  if (id === props.currentId) return
  // 항목 간 이동 시에도 현재 필터 맥락을 유지(새로고침/직접진입 복원용).
  saveQaFilter(filterState.value)
  router.push(`/qa/${id}`)
}
</script>

<template>
  <div class="flex flex-col overflow-hidden rounded-xl border border-slate-200 bg-white">
    <!-- 필터 -->
    <div class="space-y-2 border-b border-slate-100 p-3">
      <div class="relative">
        <Search class="absolute left-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-slate-400" />
        <input
          v-model="search"
          type="text"
          placeholder="제목·담당자 검색"
          class="w-full rounded-lg border border-slate-200 py-1.5 pl-8 pr-2 text-xs focus:border-emerald-300 focus:outline-none focus:ring-2 focus:ring-emerald-200"
        />
      </div>
      <div class="flex gap-2">
        <select
          v-model="status"
          class="min-w-0 flex-1 rounded-lg border border-slate-200 bg-white px-2 py-1.5 text-xs focus:outline-none focus:ring-2 focus:ring-emerald-200"
        >
          <option value="all">모든 상태</option>
          <option value="needs_fix">수정필요</option>
          <option value="in_progress">진행중</option>
          <option value="fix_done">수정완료</option>
          <option value="confirmed">확인완료</option>
          <option value="on_hold">보류</option>
          <option value="needs_recheck">추가확인필요</option>
        </select>
        <select
          v-model="priority"
          class="min-w-0 flex-1 rounded-lg border border-slate-200 bg-white px-2 py-1.5 text-xs focus:outline-none focus:ring-2 focus:ring-emerald-200"
        >
          <option value="all">모든 우선순위</option>
          <option value="critical">긴급</option>
          <option value="high">높음</option>
          <option value="medium">보통</option>
          <option value="low">낮음</option>
        </select>
      </div>
      <div class="flex items-center justify-between px-0.5">
        <button
          v-if="hasExtra"
          type="button"
          class="inline-flex items-center gap-1 text-[11px] text-emerald-600 hover:text-emerald-700"
          @click="clearExtra"
        >
          <X class="h-3 w-3" /> 추가 필터 적용 중 · 전체 보기
        </button>
        <span v-else />
        <span class="text-[11px] text-slate-400">{{ filtered.length }}건</span>
      </div>
    </div>

    <!-- 목록 -->
    <ul class="max-h-[calc(100vh-13rem)] overflow-y-auto">
      <li v-for="item in filtered" :key="item.id">
        <button
          type="button"
          :aria-current="item.id === currentId ? 'true' : undefined"
          :class="[
            'flex w-full flex-col gap-1.5 border-b border-slate-50 px-3 py-2.5 text-left transition',
            item.id === currentId
              ? 'bg-emerald-50/70 ring-1 ring-inset ring-emerald-200'
              : 'hover:bg-slate-50',
          ]"
          @click="go(item.id)"
        >
          <p
            :class="[
              'line-clamp-2 text-xs font-medium',
              item.id === currentId ? 'text-emerald-900' : 'text-slate-700',
            ]"
          >
            {{ item.title }}
          </p>
          <div class="flex items-center gap-1.5">
            <StatusBadge :status="item.status" />
            <PriorityBadge :priority="item.priority" />
          </div>
        </button>
      </li>
      <li v-if="filtered.length === 0" class="px-3 py-10 text-center text-xs text-slate-400">
        <Inbox class="mx-auto mb-2 h-5 w-5 text-slate-300" />
        해당 조건의 QA 항목이 없습니다.
      </li>
    </ul>
  </div>
</template>
