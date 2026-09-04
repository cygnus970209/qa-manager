<script setup lang="ts">
import draggable from 'vuedraggable'
import { GripVertical, Pin } from '@lucide/vue'
import AppDialog from '~/components/base/AppDialog.vue'
import StatusBadge from '~/components/base/StatusBadge.vue'
import { sortableOptions } from '~/utils/sortable'
import type { Project } from '~/types/api'

/**
 * 사이드바 프로젝트 순서 변경 (사용자별, 서버 저장).
 * 고정 프로젝트는 항상 위에 오므로 고정/나머지 두 묶음 안에서만 끌어 옮긴다.
 */
const props = defineProps<{
  open: boolean
  projects: Project[]
}>()
const emit = defineEmits<{
  close: []
  reordered: [projects: Project[]]
}>()

const projectsApi = useProjects()
const { t } = useI18n()

const pinnedList = ref<Project[]>([])
const otherList = ref<Project[]>([])
const submitting = ref(false)
const error = ref<string | null>(null)

watch(() => props.open, (v) => {
  if (!v) return
  error.value = null
  // 원본 순서를 복제해 모달 안에서만 편집 (저장 전까지 사이드바에 영향 없음)
  pinnedList.value = props.projects.filter((p) => p.pinned)
  otherList.value = props.projects.filter((p) => !p.pinned)
})

async function onSave() {
  error.value = null
  submitting.value = true
  try {
    const ids = [...pinnedList.value, ...otherList.value].map((p) => p.id)
    const reordered = await projectsApi.reorder(ids)
    emit('reordered', reordered)
    emit('close')
  } catch (e: any) {
    error.value = e?.data?.message ?? t('shell.sidebar.reorderModal.saveFailed')
  } finally {
    submitting.value = false
  }
}

const rowCls = 'flex items-center gap-3 rounded-lg border border-slate-200 bg-white px-3 py-2.5 dark:border-slate-800 dark:bg-slate-900'
</script>

<template>
  <AppDialog :open="open" :title="$t('shell.sidebar.reorderModal.title')" @close="emit('close')">
    <p class="mb-3 text-xs text-slate-400 dark:text-slate-500">{{ $t('shell.sidebar.reorderModal.hint') }}</p>

    <template v-if="pinnedList.length > 0">
      <p class="mb-1.5 flex items-center gap-1 text-[11px] font-semibold uppercase tracking-wider text-slate-400 dark:text-slate-500">
        <Pin class="h-3 w-3" /> {{ $t('shell.sidebar.reorderModal.pinnedSection') }}
      </p>
      <draggable v-model="pinnedList" item-key="id" tag="ul" class="mb-4 flex flex-col gap-2" v-bind="sortableOptions">
        <template #item="{ element: p }">
          <li :class="rowCls">
            <button type="button" class="drag-handle cursor-grab text-slate-300 hover:text-slate-500 active:cursor-grabbing dark:text-slate-600 dark:hover:text-slate-400" :title="$t('shell.sidebar.reorderModal.dragHandle')">
              <GripVertical class="h-4 w-4" />
            </button>
            <span class="min-w-0 flex-1 truncate text-sm font-medium text-slate-800 dark:text-slate-100">{{ p.name }}</span>
            <StatusBadge :status="p.status" />
          </li>
        </template>
      </draggable>
    </template>

    <p v-if="pinnedList.length > 0 && otherList.length > 0" class="mb-1.5 text-[11px] font-semibold uppercase tracking-wider text-slate-400 dark:text-slate-500">
      {{ $t('shell.sidebar.reorderModal.othersSection') }}
    </p>
    <draggable v-model="otherList" item-key="id" tag="ul" class="flex flex-col gap-2" v-bind="sortableOptions">
      <template #item="{ element: p }">
        <li :class="rowCls">
          <button type="button" class="drag-handle cursor-grab text-slate-300 hover:text-slate-500 active:cursor-grabbing dark:text-slate-600 dark:hover:text-slate-400" :title="$t('shell.sidebar.reorderModal.dragHandle')">
            <GripVertical class="h-4 w-4" />
          </button>
          <span class="min-w-0 flex-1 truncate text-sm font-medium text-slate-800 dark:text-slate-100">{{ p.name }}</span>
          <StatusBadge :status="p.status" />
        </li>
      </template>
    </draggable>

    <p v-if="pinnedList.length + otherList.length === 0" class="rounded-lg border border-dashed border-slate-200 px-4 py-6 text-center text-xs text-slate-400 dark:border-slate-800 dark:text-slate-500">
      {{ $t('shell.sidebar.reorderModal.empty') }}
    </p>
    <p v-if="error" class="mt-3 rounded bg-red-50 px-3 py-2 text-xs text-red-700 dark:bg-red-500/10 dark:text-red-300">{{ error }}</p>

    <template #footer>
      <button type="button" class="rounded-md border border-slate-200 px-3 py-1.5 text-sm text-slate-600 hover:bg-slate-50 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-800/60" @click="emit('close')">{{ $t('common.actions.cancel') }}</button>
      <button
        type="button"
        :disabled="submitting || pinnedList.length + otherList.length === 0"
        class="rounded-md bg-emerald-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-emerald-700 disabled:opacity-60"
        @click="onSave"
      >
        {{ submitting ? $t('common.state.saving') : $t('common.actions.save') }}
      </button>
    </template>
  </AppDialog>
</template>
