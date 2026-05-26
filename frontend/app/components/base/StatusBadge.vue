<script setup lang="ts">
import type { QaStatus, UpdateStatus, ProjectStatus } from '~/types/api'

type AnyStatus = QaStatus | UpdateStatus | ProjectStatus

const props = defineProps<{ status: AnyStatus }>()

const config: Record<string, { label: string; cls: string }> = {
  // QA (6단계)
  needs_fix:     { label: '수정필요',     cls: 'bg-rose-50 text-rose-600' },
  in_progress:   { label: '진행중',       cls: 'bg-blue-50 text-blue-600' },
  fix_done:      { label: '수정완료',     cls: 'bg-amber-50 text-amber-600' },
  confirmed:     { label: '확인완료',     cls: 'bg-emerald-50 text-emerald-600' },
  on_hold:       { label: '보류',         cls: 'bg-slate-100 text-slate-600' },
  needs_recheck: { label: '추가확인필요', cls: 'bg-purple-50 text-purple-600' },
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
