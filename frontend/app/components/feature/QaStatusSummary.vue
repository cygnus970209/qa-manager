<script setup lang="ts">
import { AlertTriangle, FileText } from '@lucide/vue'

/**
 * QA 현황 요약 카드 — 예전 통계 카드 8장을 한 장으로 합친 것.
 * 왼쪽 총 건수·완료율, 가운데 상태별 비율 바 + 범례, 오른쪽 긴급(우선순위 critical) 건수.
 */
interface Counts {
  total: number
  needsFix: number
  inProgress: number
  fixDone: number
  confirmed: number
  onHold: number
  needsRecheck: number
  critical: number
}
const props = defineProps<{ stats: Counts }>()

const segments = computed(() => [
  { key: 'needs_fix', count: props.stats.needsFix, cls: 'bg-rose-500' },
  { key: 'in_progress', count: props.stats.inProgress, cls: 'bg-blue-500' },
  { key: 'fix_done', count: props.stats.fixDone, cls: 'bg-amber-500' },
  { key: 'confirmed', count: props.stats.confirmed, cls: 'bg-emerald-500' },
  { key: 'on_hold', count: props.stats.onHold, cls: 'bg-slate-400' },
  { key: 'needs_recheck', count: props.stats.needsRecheck, cls: 'bg-purple-500' },
])
const rate = computed(() => (props.stats.total > 0 ? Math.round((props.stats.confirmed / props.stats.total) * 100) : 0))
function pct(n: number) {
  return props.stats.total > 0 ? (n / props.stats.total) * 100 : 0
}
</script>

<template>
  <div class="flex flex-col gap-5 rounded-xl border border-slate-200 bg-white px-6 py-5 dark:border-slate-800 dark:bg-slate-900 md:flex-row md:items-center md:gap-6">
    <div class="flex shrink-0 items-center gap-4 md:border-r md:border-slate-100 md:pr-6 md:dark:border-slate-800">
      <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-blue-50 dark:bg-blue-500/10">
        <FileText class="h-5 w-5 text-blue-500 dark:text-blue-400" />
      </div>
      <div>
        <p class="text-sm text-slate-500 dark:text-slate-400">{{ $t('dashboard.stats.totalQa') }}</p>
        <p class="mt-1 text-2xl font-bold text-slate-800 dark:text-slate-100">{{ stats.total.toLocaleString() }}</p>
        <p class="mt-1 text-xs text-slate-400 dark:text-slate-500">{{ $t('dashboard.stats.completionRate', { rate }) }}</p>
      </div>
    </div>

    <div class="min-w-0 flex-1">
      <div class="flex h-2.5 overflow-hidden rounded-full bg-slate-100 dark:bg-slate-800">
        <div v-for="s in segments" :key="s.key" :class="[s.cls, 'transition-all']" :style="{ width: `${pct(s.count)}%` }" />
      </div>
      <div class="mt-3 flex flex-wrap gap-x-5 gap-y-2">
        <span v-for="s in segments" :key="s.key" class="inline-flex items-center gap-1.5 text-xs text-slate-500 dark:text-slate-400">
          <span :class="['h-2 w-2 rounded-full', s.cls]" />
          {{ $t('common.qaStatus.' + s.key) }}
          <span class="font-semibold text-slate-700 dark:text-slate-200">{{ s.count.toLocaleString() }}</span>
        </span>
      </div>
    </div>

    <div class="flex shrink-0 items-center gap-3 md:border-l md:border-slate-100 md:pl-6 md:dark:border-slate-800">
      <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-rose-50 dark:bg-rose-500/10">
        <AlertTriangle class="h-5 w-5 text-rose-500 dark:text-rose-400" />
      </div>
      <div>
        <p class="text-sm text-slate-500 dark:text-slate-400">{{ $t('common.priority.critical') }}</p>
        <p class="mt-1 text-2xl font-bold text-slate-800 dark:text-slate-100">{{ stats.critical.toLocaleString() }}</p>
      </div>
    </div>
  </div>
</template>
