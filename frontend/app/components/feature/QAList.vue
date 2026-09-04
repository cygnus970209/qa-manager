<script setup lang="ts">
import { Search, Inbox } from '@lucide/vue'
import StatusBadge from '~/components/base/StatusBadge.vue'
import PriorityBadge from '~/components/base/PriorityBadge.vue'
import AppSelect from '~/components/base/AppSelect.vue'
import { useSelectOptions } from '~/composables/useSelectOptions'
import { applyQaFilter, saveQaFilter, type QaFilterState } from '~/utils/qaFilter'
import type { Member, Project, ProjectUpdate, QaItem } from '~/types/api'

const props = defineProps<{
  items: QaItem[]
  updates: ProjectUpdate[]
  members?: Member[]
  /** 주면 업데이트 열에 프로젝트 이름을 함께 보여준다 (대시보드처럼 여러 프로젝트가 섞인 목록) */
  projects?: Project[]
}>()

const auth = useAuthStore()

/* '배포완료 숨기기' — 상세 사이드바와 공유하는 localStorage 영속 설정 */
const HIDE_RELEASED_KEY = 'qa-filter-hide-released'
const hideReleased = ref(false)
onMounted(() => {
  const v = localStorage.getItem(HIDE_RELEASED_KEY)
  if (v !== null) hideReleased.value = v === '1'
})

const statusFilter = ref<string>('all')
const priorityFilter = ref<string>('all')
const updateFilter = ref<string>('all')
const testerFilter = ref<number | null>(null)
const assigneeFilter = ref<number | null>(null)
const mineOnly = ref(false)
const search = ref('')

const releasedUpdateIds = computed(
  () => new Set(props.updates.filter((u) => u.status === 'released').map((u) => u.id)),
)
const updateOptions = computed(() =>
  hideReleased.value ? props.updates.filter((u) => u.status !== 'released') : props.updates,
)
watch(hideReleased, (v) => {
  localStorage.setItem(HIDE_RELEASED_KEY, v ? '1' : '0')
  // 숨김을 켤 때 배포완료 업데이트가 선택돼 있으면 해제한다.
  if (v && updateFilter.value !== 'all' && releasedUpdateIds.value.has(Number(updateFilter.value))) {
    updateFilter.value = 'all'
  }
})

const filterState = computed<QaFilterState>(() => ({
  status: statusFilter.value,
  priority: priorityFilter.value,
  projectId: 'all', // 전역 목록에는 프로젝트 필터가 없음(상세 사이드바 전용 필드)
  updateId: updateFilter.value,
  testerId: testerFilter.value,
  assigneeId: assigneeFilter.value,
  mineOnly: mineOnly.value,
  search: search.value,
}))

const filtered = computed(() => applyQaFilter(
  props.items,
  filterState.value,
  auth.user?.id,
  undefined,
  hideReleased.value ? releasedUpdateIds.value : undefined,
))

const memberOptions = computed<Member[]>(() => props.members ?? [])

const { t } = useI18n()
const { qaStatus, priority } = useSelectOptions()
const statusFilterOptions = computed(() => [{ value: 'all', label: t('qa.filter.allStatuses') }, ...qaStatus.value])
/** 긴급 → 낮음 순 */
const priorityFilterOptions = computed(() => [{ value: 'all', label: t('qa.filter.allPriorities') }, ...[...priority.value].reverse()])
const updateFilterOptions = computed(() => [
  { value: 'all', label: t('qa.filter.allUpdates') },
  ...updateOptions.value.map((u) => ({ value: String(u.id), label: `${u.version} - ${u.title}` })),
])

function findUpdate(id: number) {
  return props.updates.find((u) => u.id === id)
}

const projectNameById = computed(() => new Map((props.projects ?? []).map((p) => [p.id, p.name])))
function projectNameOf(updateId: number) {
  const u = findUpdate(updateId)
  return u ? projectNameById.value.get(u.projectId) ?? null : null
}

/** 상세창 사이드바 용. 현재 필터 상태를 저장해 상세에서 동일한 목록을 재현한다. */
function rememberFilter() {
  saveQaFilter(filterState.value)
}
</script>

<template>
  <div class="overflow-hidden rounded-xl border border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-900">
    <!-- Filters -->
    <div class="flex flex-col gap-3 border-b border-slate-100 p-4 md:p-5 dark:border-slate-800">
      <div class="flex flex-wrap items-center gap-2.5">
        <div class="relative min-w-[220px] flex-1">
          <Search class="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400 dark:text-slate-500" />
          <input
            v-model="search"
            type="text"
            :placeholder="$t('qa.list.searchPlaceholder')"
            class="w-full rounded-lg border border-slate-200 py-2 pl-9 pr-3 text-sm focus:border-emerald-300 focus:outline-none focus:ring-2 focus:ring-emerald-200 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100 dark:placeholder-slate-500 dark:focus:ring-emerald-500/20"
          />
        </div>
        <AppSelect v-model="statusFilter" class="min-w-[7.5rem]" size="md" rounded="lg" :options="statusFilterOptions" />
        <AppSelect v-model="priorityFilter" class="min-w-[7.5rem]" size="md" rounded="lg" :options="priorityFilterOptions" />
        <AppSelect v-model="updateFilter" class="min-w-[9rem] max-w-[16rem]" size="md" rounded="lg" :options="updateFilterOptions" />
        <label class="flex cursor-pointer select-none items-center gap-1.5 text-xs font-medium text-slate-500 dark:text-slate-400">
          <input
            v-model="hideReleased"
            type="checkbox"
            class="h-3.5 w-3.5 rounded border-slate-300 text-blue-500 focus:ring-blue-400 dark:border-slate-600 dark:bg-slate-900"
          />
          {{ $t('qa.filter.hideReleased') }}
        </label>
      </div>

      <!-- 멤버 필터 행 -->
      <div class="flex flex-wrap items-center gap-2.5 text-xs">
        <span class="text-slate-400 dark:text-slate-500">{{ $t('qa.list.membersLabel') }}</span>
        <div class="min-w-[180px]">
          <AppSelect
            v-model="testerFilter"
            size="md"
            rounded="lg"
            searchable
            clearable
            :options="memberOptions"
            :key-fn="(m: Member) => m.id"
            :label-fn="(m: Member) => $t('qa.list.testerOption', { name: m.name })"
            :search-fn="(m: Member) => m.role ?? ''"
            :search-placeholder="$t('qa.list.searchTester')"
            :placeholder="$t('qa.list.allTesters')"
          />
        </div>
        <div class="min-w-[180px]">
          <AppSelect
            v-model="assigneeFilter"
            size="md"
            rounded="lg"
            searchable
            clearable
            :options="memberOptions"
            :key-fn="(m: Member) => m.id"
            :label-fn="(m: Member) => $t('qa.list.assigneeOption', { name: m.name })"
            :search-fn="(m: Member) => m.role ?? ''"
            :search-placeholder="$t('qa.list.searchAssignee')"
            :placeholder="$t('qa.list.allAssignees')"
          />
        </div>
        <label v-if="auth.user" class="ml-2 inline-flex cursor-pointer items-center gap-1 rounded-md border border-slate-200 px-2 py-1.5 dark:border-slate-800">
          <input v-model="mineOnly" type="checkbox" class="accent-emerald-500" />
          <span class="text-slate-600 dark:text-slate-300">{{ $t('qa.list.mineOnly') }}</span>
        </label>
        <span class="ml-auto whitespace-nowrap text-slate-400 dark:text-slate-500">{{ $t('qa.list.totalCount', filtered.length) }}</span>
      </div>
    </div>

    <!-- Table -->
    <div class="overflow-x-auto">
      <table class="w-full text-left">
        <thead>
          <tr class="border-b border-slate-100 bg-slate-50 dark:border-slate-800 dark:bg-slate-800/50">
            <th class="w-full px-5 py-3.5 text-xs font-medium text-slate-500 dark:text-slate-400">{{ $t('qa.fields.title') }}</th>
            <th class="whitespace-nowrap px-5 py-3.5 text-xs font-medium text-slate-500 dark:text-slate-400 hidden md:table-cell">
              <template v-if="projects">{{ $t('qa.fields.project') }} · {{ $t('qa.fields.update') }}</template>
              <template v-else>{{ $t('qa.fields.update') }}</template>
            </th>
            <th class="whitespace-nowrap px-5 py-3.5 text-xs font-medium text-slate-500 dark:text-slate-400">{{ $t('qa.fields.status') }}</th>
            <th class="whitespace-nowrap px-5 py-3.5 text-xs font-medium text-slate-500 dark:text-slate-400">{{ $t('qa.fields.priority') }}</th>
            <th class="whitespace-nowrap px-5 py-3.5 text-xs font-medium text-slate-500 dark:text-slate-400 hidden sm:table-cell">{{ $t('common.roles.assignee') }}</th>
            <th class="whitespace-nowrap px-5 py-3.5 text-xs font-medium text-slate-500 dark:text-slate-400 hidden lg:table-cell">{{ $t('qa.list.colUpdatedAt') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="item in filtered"
            :key="item.id"
            class="cursor-pointer border-b border-slate-50 transition hover:bg-slate-50 dark:border-slate-800 dark:hover:bg-slate-800/60"
            @click="rememberFilter(); $router.push(`/qa/${item.id}`)"
          >
            <td class="px-5 py-4">
              <p class="line-clamp-1 text-sm font-medium text-slate-800 dark:text-slate-100"><span class="mr-1.5 font-normal tabular-nums text-slate-400 dark:text-slate-500">#{{ item.id }}</span>{{ item.title }}</p>
              <!-- wrapper 가 hidden/block 을 담당해, 클램프(display:-webkit-box)가 sm:block 에 덮이지 않게 함 -->
              <div class="mt-1 hidden sm:block">
                <p class="line-clamp-2 text-xs text-slate-400 dark:text-slate-500">{{ item.description }}</p>
              </div>
            </td>
            <td class="hidden whitespace-nowrap px-5 py-4 md:table-cell">
              <div class="flex flex-col items-start gap-1">
                <span v-if="projects" class="text-xs text-slate-600 dark:text-slate-300">{{ projectNameOf(item.updateId) ?? '-' }}</span>
                <span class="rounded bg-slate-100 px-2 py-0.5 text-xs text-slate-500 dark:bg-slate-800 dark:text-slate-400">
                  {{ findUpdate(item.updateId)?.version ?? '-' }}
                </span>
              </div>
            </td>
            <td class="whitespace-nowrap px-5 py-4"><StatusBadge :status="item.status" /></td>
            <td class="whitespace-nowrap px-5 py-4"><PriorityBadge :priority="item.priority" /></td>
            <td class="hidden whitespace-nowrap px-5 py-4 sm:table-cell">
              <div class="flex flex-col gap-1 text-xs">
                <span v-if="item.tester" class="text-slate-500 dark:text-slate-400">
                  T: {{ item.tester.name }}
                </span>
                <span v-if="item.assignee1 || item.assignee2" class="text-slate-700 dark:text-slate-200">
                  {{ [item.assignee1?.name, item.assignee2?.name].filter(Boolean).join(', ') }}
                </span>
                <span v-if="!item.tester && !item.assignee1 && !item.assignee2" class="text-slate-400 dark:text-slate-500">{{ $t('qa.common.unassigned') }}</span>
              </div>
            </td>
            <td class="hidden whitespace-nowrap px-5 py-4 text-xs text-slate-400 lg:table-cell dark:text-slate-500">
              {{ item.updatedAt?.slice(0, 10) }}
            </td>
          </tr>
          <tr v-if="filtered.length === 0">
            <td colspan="6" class="px-5 py-12 text-center text-sm text-slate-400 dark:text-slate-500">
              <Inbox class="mx-auto mb-2 h-6 w-6 text-slate-300 dark:text-slate-600" />
              {{ $t('qa.filter.noMatch') }}
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
