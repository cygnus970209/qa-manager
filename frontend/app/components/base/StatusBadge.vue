<script setup lang="ts">
import type { QaStatus, UpdateStatus, ProjectStatus } from '~/types/api'

type AnyStatus = QaStatus | UpdateStatus | ProjectStatus

const props = defineProps<{ status: AnyStatus }>()

const config: Record<string, { label: string; cls: string }> = {
  // QA
  pending:     { label: '대기중',     cls: 'bg-slate-100 text-slate-600' },
  in_progress: { label: '진행중',     cls: 'bg-blue-50 text-blue-600' },
  resolved:    { label: '해결됨',     cls: 'bg-emerald-50 text-emerald-600' },
  closed:      { label: '종료',       cls: 'bg-slate-50 text-slate-500' },
  // Update
  testing:     { label: '테스트',     cls: 'bg-amber-50 text-amber-600' },
  released:    { label: '배포완료',   cls: 'bg-emerald-50 text-emerald-600' },
  // Project
  active:      { label: '진행중',     cls: 'bg-emerald-50 text-emerald-600' },
  paused:      { label: '일시중지',   cls: 'bg-amber-50 text-amber-600' },
  completed:   { label: '완료',       cls: 'bg-slate-50 text-slate-500' },
}

const view = computed(() => config[props.status] ?? { label: props.status, cls: 'bg-slate-100 text-slate-600' })
</script>

<template>
  <span :class="['inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium', view.cls]">
    {{ view.label }}
  </span>
</template>
