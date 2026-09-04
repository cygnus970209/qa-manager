<script setup lang="ts">
import { ChevronDown, Pencil, Plus, Trash2 } from '@lucide/vue'
import StatusBadge from '~/components/base/StatusBadge.vue'
import PriorityBadge from '~/components/base/PriorityBadge.vue'
import ExpandableText from '~/components/base/ExpandableText.vue'
import { emptyQaFilter, saveQaFilter } from '~/utils/qaFilter'
import TestRunSummary from '~/components/feature/testing/TestRunSummary.vue'
import type { ProjectUpdate, QaItem, QaStatus, QaStatusUpper, TestRun, UpdateStatus } from '~/types/api'

const props = defineProps<{
  update: ProjectUpdate
  items: QaItem[]
  runs?: TestRun[]
  defaultOpen?: boolean
}>()
const emit = defineEmits<{
  changeStatus: [updateId: number, status: 'IN_PROGRESS' | 'TESTING' | 'RELEASED']
  addQa: [updateId: number]
  newRun: [updateId: number]
  edit: [update: ProjectUpdate]
  remove: [update: ProjectUpdate]
  changeQaStatus: [qaId: number, status: QaStatusUpper]
}>()

const open = ref(!!props.defaultOpen)

/** 헤더에 표시할 상태별 QA 개수 (0건인 상태는 생략). 색상은 StatusBadge 와 동일. */
const STATUS_META: { key: QaStatus; cls: string }[] = [
  { key: 'needs_fix',     cls: 'bg-rose-50 text-rose-600 dark:bg-rose-500/10 dark:text-rose-400' },
  { key: 'in_progress',   cls: 'bg-blue-50 text-blue-600 dark:bg-blue-500/10 dark:text-blue-400' },
  { key: 'fix_done',      cls: 'bg-amber-50 text-amber-600 dark:bg-amber-500/10 dark:text-amber-400' },
  { key: 'confirmed',     cls: 'bg-emerald-50 text-emerald-600 dark:bg-emerald-500/10 dark:text-emerald-400' },
  { key: 'on_hold',       cls: 'bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-300' },
  { key: 'needs_recheck', cls: 'bg-purple-50 text-purple-600 dark:bg-purple-500/10 dark:text-purple-400' },
]
const statusCounts = computed(() =>
  STATUS_META
    .map((m) => ({ ...m, count: props.items.filter((q) => q.status === m.key).length }))
    .filter((m) => m.count > 0),
)

const upperStatus = computed<'IN_PROGRESS' | 'TESTING' | 'RELEASED'>(() => {
  switch (props.update.status) {
    case 'in_progress': return 'IN_PROGRESS'
    case 'testing':     return 'TESTING'
    case 'released':    return 'RELEASED'
  }
})

function onChangeStatus(e: Event) {
  const v = (e.target as HTMLSelectElement).value as 'IN_PROGRESS' | 'TESTING' | 'RELEASED'
  emit('changeStatus', props.update.id, v)
}

function onChangeQaStatus(qaId: number, e: Event) {
  const v = (e.target as HTMLSelectElement).value as QaStatusUpper
  emit('changeQaStatus', qaId, v)
}

/** QA 행 클릭 시 상세 사이드바가 이 프로젝트·업데이트로 스코프되도록 필터를 저장. */
function rememberFilter() {
  saveQaFilter({
    ...emptyQaFilter(),
    projectId: String(props.update.projectId),
    updateId: String(props.update.id),
  })
}
</script>

<template>
  <div class="rounded-xl border border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-900">
    <button
      type="button"
      class="flex w-full items-center justify-between gap-3 px-5 py-3 text-left transition hover:bg-slate-50 dark:hover:bg-slate-800/60"
      @click="open = !open"
    >
      <div class="flex min-w-0 items-center gap-3">
        <ChevronDown
          :class="['h-4 w-4 text-slate-400 transition-transform dark:text-slate-500', open && 'rotate-180']"
        />
        <span class="rounded bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-600 dark:bg-slate-800 dark:text-slate-300">
          {{ update.version }}
        </span>
        <span class="truncate text-sm font-semibold text-slate-800 dark:text-slate-100">{{ update.title }}</span>
        <StatusBadge :status="update.status" />
      </div>
      <div class="flex shrink-0 flex-wrap items-center justify-end gap-1.5 text-xs text-slate-400 dark:text-slate-500">
        <span>{{ $t('project.accordion.qaCount', items.length) }}</span>
        <span
          v-for="sc in statusCounts"
          :key="sc.key"
          :class="['inline-flex items-center whitespace-nowrap rounded-full px-2 py-0.5 font-medium', sc.cls]"
        >
          {{ $t('common.qaStatus.' + sc.key) }} {{ sc.count }}
        </span>
      </div>
    </button>

    <div v-if="open" class="border-t border-slate-100 px-5 py-4 dark:border-slate-800">
      <ExpandableText v-if="update.description" :text="update.description" :lines="3" class="mb-3" />
      <div class="mb-3 flex items-center gap-2">
        <select
          class="rounded-md border border-slate-200 bg-white px-2 py-1 text-xs focus:outline-none focus:ring-2 focus:ring-emerald-200 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-100 dark:focus:ring-emerald-500/20"
          :value="upperStatus"
          @change="onChangeStatus"
          @click.stop
        >
          <option value="IN_PROGRESS">{{ $t('project.accordion.statusOption', { status: $t('common.updateStatus.in_progress') }) }}</option>
          <option value="TESTING">{{ $t('project.accordion.statusOption', { status: $t('common.updateStatus.testing') }) }}</option>
          <option value="RELEASED">{{ $t('project.accordion.statusOption', { status: $t('common.updateStatus.released') }) }}</option>
        </select>
        <button
          type="button"
          class="ml-auto inline-flex items-center gap-1 rounded-md bg-blue-500 px-2.5 py-1 text-xs font-medium text-white hover:bg-blue-600"
          @click.stop="emit('addQa', update.id)"
        >
          <Plus class="h-3.5 w-3.5" /> {{ $t('project.accordion.addQa') }}
        </button>
        <button
          type="button"
          class="inline-flex h-7 w-7 items-center justify-center rounded-md border border-slate-200 text-slate-500 hover:bg-slate-50 hover:text-emerald-600 dark:border-slate-800 dark:text-slate-400 dark:hover:bg-slate-800/60 dark:hover:text-emerald-400"
          :title="$t('project.accordion.editUpdate')"
          @click.stop="emit('edit', update)"
        >
          <Pencil class="h-3.5 w-3.5" />
        </button>
        <button
          type="button"
          class="inline-flex h-7 w-7 items-center justify-center rounded-md border border-slate-200 text-slate-500 hover:bg-red-50 hover:text-red-600 dark:border-slate-800 dark:text-slate-400 dark:hover:bg-red-500/10 dark:hover:text-red-400"
          :title="$t('project.accordion.deleteUpdate')"
          @click.stop="emit('remove', update)"
        >
          <Trash2 class="h-3.5 w-3.5" />
        </button>
      </div>

      <!-- 테스트 런 요약 -->
      <div class="mb-3">
        <TestRunSummary :runs="runs ?? []" @new-run="emit('newRun', update.id)" />
      </div>

      <ul v-if="items.length > 0" class="divide-y divide-slate-100 overflow-hidden rounded-lg border border-slate-200 dark:divide-slate-800 dark:border-slate-800">
        <li
          v-for="q in items"
          :key="q.id"
          class="cursor-pointer px-4 py-3 transition hover:bg-slate-50 dark:hover:bg-slate-800/60"
          @click="rememberFilter(); $router.push(`/qa/${q.id}`)"
        >
          <div class="flex items-start justify-between gap-3">
            <div class="min-w-0">
              <p class="line-clamp-1 text-sm font-medium text-slate-800 dark:text-slate-100"><span class="mr-1.5 font-normal tabular-nums text-slate-400 dark:text-slate-500">#{{ q.id }}</span>{{ q.title }}</p>
              <p v-if="q.description" class="mt-0.5 line-clamp-1 text-xs text-slate-400 dark:text-slate-500">{{ q.description }}</p>
            </div>
            <div class="flex shrink-0 items-center gap-2">
              <select
                :value="q.status.toUpperCase()"
                class="cursor-pointer rounded-md border border-slate-200 bg-white px-2 py-1 text-xs focus:outline-none focus:ring-2 focus:ring-emerald-200 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-100 dark:focus:ring-emerald-500/20"
                @click.stop
                @change="onChangeQaStatus(q.id, $event)"
              >
                <option value="NEEDS_FIX">{{ $t('common.qaStatus.needs_fix') }}</option>
                <option value="IN_PROGRESS">{{ $t('common.qaStatus.in_progress') }}</option>
                <option value="FIX_DONE">{{ $t('common.qaStatus.fix_done') }}</option>
                <option value="CONFIRMED">{{ $t('common.qaStatus.confirmed') }}</option>
                <option value="ON_HOLD">{{ $t('common.qaStatus.on_hold') }}</option>
                <option value="NEEDS_RECHECK">{{ $t('common.qaStatus.needs_recheck') }}</option>
              </select>
              <PriorityBadge :priority="q.priority" />
            </div>
          </div>
        </li>
      </ul>
      <div v-else class="rounded-lg border border-dashed border-slate-200 px-4 py-6 text-center text-xs text-slate-400 dark:border-slate-800 dark:text-slate-500">
        {{ $t('project.accordion.emptyQa') }}
      </div>
    </div>
  </div>
</template>
