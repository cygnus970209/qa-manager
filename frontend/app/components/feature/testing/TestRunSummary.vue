<script setup lang="ts">
import { ListChecks, Plus } from '@lucide/vue'
import type { TestRun, TestRunStats } from '~/types/api'

const props = defineProps<{ runs: TestRun[] }>()
const emit = defineEmits<{ 'new-run': [] }>()

/** 최근 생성 순으로 표시 */
const sortedRuns = computed(() =>
  [...props.runs].sort((a, b) => (b.createdAt ?? '').localeCompare(a.createdAt ?? '')),
)

/** 실행완료 수 (PENDING 제외) */
function executed(s: TestRunStats) {
  return s.total - s.pending
}
function percent(s: TestRunStats) {
  return s.total > 0 ? Math.round((executed(s) / s.total) * 100) : 0
}

/** 결과별 칩 메타 — 0건인 결과는 생략 */
const CHIP_META = [
  { key: 'pass',    cls: 'bg-emerald-50 text-emerald-600 dark:bg-emerald-500/10 dark:text-emerald-400' },
  { key: 'fail',    cls: 'bg-rose-50 text-rose-600 dark:bg-rose-500/10 dark:text-rose-400' },
  { key: 'blocked', cls: 'bg-amber-50 text-amber-600 dark:bg-amber-500/10 dark:text-amber-400' },
  { key: 'skip',    cls: 'bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-300' },
] as const
function chips(s: TestRunStats) {
  return CHIP_META.map((m) => ({ ...m, count: s[m.key] })).filter((c) => c.count > 0)
}
</script>

<template>
  <div class="rounded-lg border border-slate-200 dark:border-slate-800">
    <div class="flex items-center justify-between gap-2 px-3 py-2">
      <span class="inline-flex min-w-0 items-center gap-1.5 text-xs font-semibold text-slate-600 dark:text-slate-300">
        <ListChecks class="h-3.5 w-3.5 shrink-0 text-slate-400 dark:text-slate-500" />
        {{ $t('testrun.summary.title') }}
        <span v-if="runs.length === 0" class="truncate font-normal text-slate-400 dark:text-slate-500">{{ $t('testrun.summary.empty') }}</span>
      </span>
      <button
        type="button"
        class="inline-flex shrink-0 items-center gap-1 rounded-md border border-slate-200 px-2 py-1 text-xs font-medium text-slate-600 hover:bg-slate-50 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-800/60"
        @click.stop="emit('new-run')"
      >
        <Plus class="h-3.5 w-3.5" /> {{ $t('testrun.summary.newRun') }}
      </button>
    </div>

    <ul v-if="runs.length > 0" class="divide-y divide-slate-100 border-t border-slate-100 dark:divide-slate-800 dark:border-slate-800">
      <li v-for="r in sortedRuns" :key="r.id">
        <NuxtLink
          :to="`/run/${r.id}`"
          class="flex items-center gap-2 px-3 py-2 transition hover:bg-slate-50 dark:hover:bg-slate-800/60"
        >
          <span class="min-w-0 flex-1 truncate text-xs font-medium text-slate-700 dark:text-slate-200">{{ r.name }}</span>
          <span
            v-if="r.closedAt"
            class="shrink-0 whitespace-nowrap rounded-full bg-slate-100 px-1.5 py-0.5 text-[10px] font-medium text-slate-500 dark:bg-slate-800 dark:text-slate-400"
          >{{ $t('testrun.summary.closedBadge') }}</span>
          <span
            v-for="c in chips(r.stats)"
            :key="c.key"
            :class="['shrink-0 whitespace-nowrap rounded-full px-1.5 py-0.5 text-[10px] font-medium', c.cls]"
          >{{ $t('testrun.result.' + c.key) }} {{ c.count }}</span>
          <span
            class="flex shrink-0 items-center gap-1.5"
            :title="$t('testrun.summary.progressTitle', { executed: executed(r.stats), total: r.stats.total })"
          >
            <span class="h-1.5 w-20 overflow-hidden rounded-full bg-slate-100 dark:bg-slate-800">
              <span class="block h-full rounded-full bg-emerald-500 dark:bg-emerald-400" :style="{ width: percent(r.stats) + '%' }" />
            </span>
            <span class="text-[10px] tabular-nums text-slate-400 dark:text-slate-500">{{ executed(r.stats) }}/{{ r.stats.total }}</span>
          </span>
        </NuxtLink>
      </li>
    </ul>
  </div>
</template>
