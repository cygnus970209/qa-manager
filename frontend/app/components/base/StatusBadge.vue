<script setup lang="ts">
import type { QaStatus, UpdateStatus, ProjectStatus } from '~/types/api'

type AnyStatus = QaStatus | UpdateStatus | ProjectStatus

const props = defineProps<{ status: AnyStatus }>()

const { t } = useI18n()

const config: Record<string, { key: string; cls: string }> = {
  // QA (6단계) — in_progress 는 QA/Update 공통 코드(라벨 동일)
  needs_fix:     { key: 'common.qaStatus.needs_fix',     cls: 'bg-rose-50 text-rose-600' },
  in_progress:   { key: 'common.qaStatus.in_progress',   cls: 'bg-blue-50 text-blue-600' },
  fix_done:      { key: 'common.qaStatus.fix_done',      cls: 'bg-amber-50 text-amber-600' },
  confirmed:     { key: 'common.qaStatus.confirmed',     cls: 'bg-emerald-50 text-emerald-600' },
  on_hold:       { key: 'common.qaStatus.on_hold',       cls: 'bg-slate-100 text-slate-600' },
  needs_recheck: { key: 'common.qaStatus.needs_recheck', cls: 'bg-purple-50 text-purple-600' },
  // Update
  testing:     { key: 'common.updateStatus.testing',  cls: 'bg-amber-50 text-amber-600' },
  released:    { key: 'common.updateStatus.released', cls: 'bg-emerald-50 text-emerald-600' },
  // Project
  active:      { key: 'common.projectStatus.active',    cls: 'bg-emerald-50 text-emerald-600' },
  paused:      { key: 'common.projectStatus.paused',    cls: 'bg-amber-50 text-amber-600' },
  completed:   { key: 'common.projectStatus.completed', cls: 'bg-slate-50 text-slate-500' },
}

const view = computed(() => {
  const c = config[props.status]
  if (!c) return { label: props.status, cls: 'bg-slate-100 text-slate-600' }
  return { label: t(c.key), cls: c.cls }
})
</script>

<template>
  <span :class="['inline-flex items-center whitespace-nowrap px-2.5 py-0.5 rounded-full text-xs font-medium', view.cls]">
    {{ view.label }}
  </span>
</template>
