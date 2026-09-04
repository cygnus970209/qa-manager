<script setup lang="ts">
import { Pencil, Pin, PinOff, Trash2 } from '@lucide/vue'
import StatusBadge from '~/components/base/StatusBadge.vue'
import ExpandableText from '~/components/base/ExpandableText.vue'
import AppSelect from '~/components/base/AppSelect.vue'
import { upperOptions, useSelectOptions } from '~/composables/useSelectOptions'
import type { Project } from '~/types/api'

const props = defineProps<{
  project: Project
  totalQA: number
  resolvedCount: number
  updateCount: number
}>()
const emit = defineEmits<{
  changeStatus: [status: 'ACTIVE' | 'PAUSED' | 'COMPLETED']
  edit: []
  remove: []
  togglePin: []
}>()

const { t } = useI18n()
const { projectStatus } = useSelectOptions()
/** "상태: 진행중" 꼴 라벨, 값은 API enum */
const statusOptions = computed(() => upperOptions(projectStatus.value)
  .map((o) => ({ value: o.value, label: t('project.header.statusOption', { status: o.label }) })))

const progress = computed(() => (props.totalQA > 0
  ? Math.round((props.resolvedCount / props.totalQA) * 100)
  : 0))

function onSelectStatus(v: 'ACTIVE' | 'PAUSED' | 'COMPLETED') {
  emit('changeStatus', v)
}
</script>

<template>
  <header class="rounded-xl border border-slate-200 bg-white p-5 md:p-6 dark:border-slate-800 dark:bg-slate-900">
    <div class="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
      <div class="min-w-0">
        <h1 class="truncate text-xl font-bold text-slate-800 md:text-2xl dark:text-slate-100">{{ project.name }}</h1>
        <ExpandableText v-if="project.description" :text="project.description" :lines="3" class="mt-2 max-w-3xl" />
        <div class="mt-3 flex items-center gap-2 text-xs text-slate-400 dark:text-slate-500">
          <span>{{ $t('project.header.createdAt', { date: project.createdAt?.slice(0, 10) }) }}</span>
          <span>·</span>
          <span>{{ $t('project.header.updateCount', updateCount) }}</span>
          <span>·</span>
          <span>{{ $t('project.header.qaSummary', { total: totalQA, resolved: resolvedCount }) }}</span>
        </div>
      </div>

      <div class="flex shrink-0 items-center gap-3">
        <button
          type="button"
          :class="[
            'inline-flex h-8 w-8 items-center justify-center rounded-md border transition',
            project.pinned
              ? 'border-emerald-200 bg-emerald-50 text-emerald-600 hover:bg-emerald-100 dark:border-emerald-500/30 dark:bg-emerald-500/10 dark:text-emerald-400 dark:hover:bg-emerald-500/20'
              : 'border-slate-200 text-slate-500 hover:bg-slate-50 hover:text-slate-700 dark:border-slate-800 dark:text-slate-400 dark:hover:bg-slate-800/60 dark:hover:text-slate-200',
          ]"
          :title="project.pinned ? $t('project.header.unpin') : $t('project.header.pin')"
          @click="emit('togglePin')"
        >
          <component :is="project.pinned ? Pin : PinOff" class="h-4 w-4" />
        </button>
        <StatusBadge :status="project.status" />
        <AppSelect
          size="sm"
          :model-value="project.status === 'active' ? 'ACTIVE' : project.status === 'paused' ? 'PAUSED' : 'COMPLETED'"
          :options="statusOptions"
          @update:model-value="onSelectStatus"
        />
        <button
          type="button"
          class="inline-flex h-8 w-8 items-center justify-center rounded-md border border-slate-200 text-slate-500 hover:bg-slate-50 hover:text-emerald-600 dark:border-slate-800 dark:text-slate-400 dark:hover:bg-slate-800/60 dark:hover:text-emerald-400"
          :title="$t('project.header.editProject')"
          @click="emit('edit')"
        >
          <Pencil class="h-4 w-4" />
        </button>
        <button
          type="button"
          class="inline-flex h-8 w-8 items-center justify-center rounded-md border border-slate-200 text-slate-500 hover:bg-red-50 hover:text-red-600 dark:border-slate-800 dark:text-slate-400 dark:hover:bg-red-500/10 dark:hover:text-red-400"
          :title="$t('project.header.deleteProject')"
          @click="emit('remove')"
        >
          <Trash2 class="h-4 w-4" />
        </button>
      </div>
    </div>

    <div class="mt-4 flex items-center gap-3">
      <div class="h-1.5 flex-1 overflow-hidden rounded-full bg-slate-100 dark:bg-slate-800">
        <div class="h-full rounded-full bg-emerald-400 transition-all" :style="{ width: `${progress}%` }" />
      </div>
      <span class="text-xs font-medium text-slate-500 dark:text-slate-400">{{ progress }}%</span>
    </div>
  </header>
</template>
