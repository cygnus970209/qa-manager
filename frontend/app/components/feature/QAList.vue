<script setup lang="ts">
import { Search, Inbox } from '@lucide/vue'
import StatusBadge from '~/components/base/StatusBadge.vue'
import PriorityBadge from '~/components/base/PriorityBadge.vue'
import SearchableSelect from '~/components/base/SearchableSelect.vue'
import type { Member, ProjectUpdate, QaItem } from '~/types/api'

const props = defineProps<{
  items: QaItem[]
  updates: ProjectUpdate[]
  members?: Member[]
}>()

const auth = useAuthStore()

const statusFilter = ref<string>('all')
const priorityFilter = ref<string>('all')
const updateFilter = ref<string>('all')
const testerFilter = ref<number | null>(null)
const assigneeFilter = ref<number | null>(null)
const mineOnly = ref(false)
const search = ref('')

const filtered = computed(() => props.items.filter((item) => {
  if (statusFilter.value !== 'all' && item.status !== statusFilter.value) return false
  if (priorityFilter.value !== 'all' && item.priority !== priorityFilter.value) return false
  if (updateFilter.value !== 'all' && String(item.updateId) !== updateFilter.value) return false

  if (testerFilter.value != null && item.tester?.id !== testerFilter.value) return false
  if (assigneeFilter.value != null
    && item.assignee1?.id !== assigneeFilter.value
    && item.assignee2?.id !== assigneeFilter.value) return false

  if (mineOnly.value && auth.user) {
    const meId = auth.user.id
    const matchMine = item.tester?.id === meId
      || item.assignee1?.id === meId
      || item.assignee2?.id === meId
    if (!matchMine) return false
  }

  if (search.value.trim()) {
    const s = search.value.toLowerCase()
    const names = [item.tester?.name, item.assignee1?.name, item.assignee2?.name]
      .filter((x): x is string => Boolean(x))
      .join(' ')
      .toLowerCase()
    return (
      item.title.toLowerCase().includes(s) ||
      (item.description ?? '').toLowerCase().includes(s) ||
      names.includes(s)
    )
  }
  return true
}))

const memberOptions = computed<Member[]>(() => props.members ?? [])

function findUpdate(id: number) {
  return props.updates.find((u) => u.id === id)
}

/** 상세창 prev/next 용. 현재 필터 결과의 ID 목록을 sessionStorage 에 저장. */
function rememberOrder(targetId: number) {
  if (!import.meta.client) return
  const ids = filtered.value.map((x) => x.id)
  sessionStorage.setItem('qa:nav:list', JSON.stringify(ids))
  sessionStorage.setItem('qa:nav:from', String(targetId))
}
</script>

<template>
  <div class="overflow-hidden rounded-xl border border-slate-200 bg-white">
    <!-- Filters -->
    <div class="flex flex-col gap-3 border-b border-slate-100 p-4">
      <div class="flex flex-wrap items-center gap-2">
        <div class="relative min-w-[200px] flex-1">
          <Search class="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
          <input
            v-model="search"
            type="text"
            placeholder="QA 항목 검색..."
            class="w-full rounded-lg border border-slate-200 py-2 pl-9 pr-3 text-sm focus:border-emerald-300 focus:outline-none focus:ring-2 focus:ring-emerald-200"
          />
        </div>
        <select
          v-model="statusFilter"
          class="rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-200"
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
          v-model="priorityFilter"
          class="rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-200"
        >
          <option value="all">모든 우선순위</option>
          <option value="critical">긴급</option>
          <option value="high">높음</option>
          <option value="medium">보통</option>
          <option value="low">낮음</option>
        </select>
        <select
          v-model="updateFilter"
          class="rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-200"
        >
          <option value="all">모든 업데이트</option>
          <option v-for="u in updates" :key="u.id" :value="String(u.id)">
            {{ u.version }} - {{ u.title }}
          </option>
        </select>
      </div>

      <!-- 멤버 필터 행 -->
      <div class="flex flex-wrap items-center gap-2 text-xs">
        <span class="text-slate-400">멤버:</span>
        <div class="min-w-[180px]">
          <SearchableSelect
            :model-value="testerFilter"
            :options="memberOptions"
            :key-fn="(m: Member) => m.id"
            :label-fn="(m: Member) => `테스터: ${m.name}`"
            :search-fn="(m: Member) => m.role ?? ''"
            placeholder="테스터 검색"
            empty-label="모든 테스터"
            clearable
            @update:model-value="(v) => testerFilter = v as number | null"
          />
        </div>
        <div class="min-w-[180px]">
          <SearchableSelect
            :model-value="assigneeFilter"
            :options="memberOptions"
            :key-fn="(m: Member) => m.id"
            :label-fn="(m: Member) => `담당자: ${m.name}`"
            :search-fn="(m: Member) => m.role ?? ''"
            placeholder="담당자 검색"
            empty-label="모든 담당자"
            clearable
            @update:model-value="(v) => assigneeFilter = v as number | null"
          />
        </div>
        <label v-if="auth.user" class="ml-2 inline-flex cursor-pointer items-center gap-1 rounded-md border border-slate-200 px-2 py-1.5">
          <input v-model="mineOnly" type="checkbox" class="accent-emerald-500" />
          <span class="text-slate-600">내 것만</span>
        </label>
        <span class="ml-auto whitespace-nowrap text-slate-400">총 {{ filtered.length }}건</span>
      </div>
    </div>

    <!-- Table -->
    <div class="overflow-x-auto">
      <table class="w-full text-left">
        <thead>
          <tr class="border-b border-slate-100 bg-slate-50">
            <th class="px-4 py-3 text-xs font-medium text-slate-500">제목</th>
            <th class="hidden px-4 py-3 text-xs font-medium text-slate-500 md:table-cell">업데이트</th>
            <th class="px-4 py-3 text-xs font-medium text-slate-500">상태</th>
            <th class="px-4 py-3 text-xs font-medium text-slate-500">우선순위</th>
            <th class="hidden px-4 py-3 text-xs font-medium text-slate-500 sm:table-cell">담당자</th>
            <th class="hidden px-4 py-3 text-xs font-medium text-slate-500 lg:table-cell">수정일</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="item in filtered"
            :key="item.id"
            class="cursor-pointer border-b border-slate-50 transition hover:bg-slate-50"
            @click="rememberOrder(item.id); $router.push(`/qa/${item.id}`)"
          >
            <td class="px-4 py-3">
              <p class="line-clamp-1 text-sm font-medium text-slate-800">{{ item.title }}</p>
              <p class="mt-0.5 line-clamp-1 hidden text-xs text-slate-400 sm:block">
                {{ item.description }}
              </p>
            </td>
            <td class="hidden px-4 py-3 md:table-cell">
              <span class="rounded bg-slate-100 px-2 py-0.5 text-xs text-slate-500">
                {{ findUpdate(item.updateId)?.version ?? '-' }}
              </span>
            </td>
            <td class="px-4 py-3"><StatusBadge :status="item.status" /></td>
            <td class="px-4 py-3"><PriorityBadge :priority="item.priority" /></td>
            <td class="hidden px-4 py-3 sm:table-cell">
              <div class="flex flex-col gap-0.5 text-xs">
                <span v-if="item.tester" class="text-slate-500">
                  T: {{ item.tester.name }}
                </span>
                <span v-if="item.assignee1 || item.assignee2" class="text-slate-700">
                  {{ [item.assignee1?.name, item.assignee2?.name].filter(Boolean).join(', ') }}
                </span>
                <span v-if="!item.tester && !item.assignee1 && !item.assignee2" class="text-slate-400">미지정</span>
              </div>
            </td>
            <td class="hidden px-4 py-3 text-xs text-slate-400 lg:table-cell">
              {{ item.updatedAt?.slice(0, 10) }}
            </td>
          </tr>
          <tr v-if="filtered.length === 0">
            <td colspan="6" class="px-4 py-12 text-center text-sm text-slate-400">
              <Inbox class="mx-auto mb-2 h-6 w-6 text-slate-300" />
              해당 조건의 QA 항목이 없습니다.
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
