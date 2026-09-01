<script setup lang="ts">
import draggable from 'vuedraggable'
import { GripVertical } from '@lucide/vue'
import AppDialog from '~/components/base/AppDialog.vue'
import StatusBadge from '~/components/base/StatusBadge.vue'
import type { ProjectUpdate } from '~/types/api'

const props = defineProps<{
  open: boolean
  projectId: number
  updates: ProjectUpdate[]
}>()
const emit = defineEmits<{
  close: []
  reordered: [updates: ProjectUpdate[]]
}>()

const updatesApi = useUpdates()
const { t } = useI18n()

const list = ref<ProjectUpdate[]>([])
const submitting = ref(false)
const error = ref<string | null>(null)

watch(() => props.open, (v) => {
  if (!v) return
  error.value = null
  // 원본 순서를 복제해 모달 안에서만 편집 (저장 전까지 화면에 영향 없음)
  list.value = [...props.updates]
})

async function onSave() {
  error.value = null
  submitting.value = true
  try {
    const reordered = await updatesApi.reorder(props.projectId, list.value.map((u) => u.id))
    emit('reordered', reordered)
    emit('close')
  } catch (e: any) {
    error.value = e?.data?.message ?? t('project.reorderModal.saveFailed')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <AppDialog :open="open" :title="$t('project.reorderModal.title')" @close="emit('close')">
    <p class="mb-3 text-xs text-slate-400 dark:text-slate-500">{{ $t('project.reorderModal.hint') }}</p>
    <draggable
      v-model="list"
      item-key="id"
      handle=".drag-handle"
      tag="ul"
      class="flex flex-col gap-2"
      ghost-class="opacity-50"
    >
      <template #item="{ element: u }">
        <li class="flex items-center gap-3 rounded-lg border border-slate-200 bg-white px-3 py-2.5 dark:border-slate-800 dark:bg-slate-900">
          <button
            type="button"
            class="drag-handle cursor-grab text-slate-300 hover:text-slate-500 active:cursor-grabbing dark:text-slate-600 dark:hover:text-slate-400"
            :title="$t('project.reorderModal.dragHandle')"
          >
            <GripVertical class="h-4 w-4" />
          </button>
          <span class="shrink-0 rounded bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-600 dark:bg-slate-800 dark:text-slate-300">
            {{ u.version }}
          </span>
          <span class="min-w-0 flex-1 truncate text-sm font-medium text-slate-800 dark:text-slate-100">{{ u.title }}</span>
          <StatusBadge :status="u.status" />
        </li>
      </template>
    </draggable>
    <p v-if="list.length === 0" class="rounded-lg border border-dashed border-slate-200 px-4 py-6 text-center text-xs text-slate-400 dark:border-slate-800 dark:text-slate-500">
      {{ $t('project.reorderModal.empty') }}
    </p>
    <p v-if="error" class="mt-3 rounded bg-red-50 px-3 py-2 text-xs text-red-700 dark:bg-red-500/10 dark:text-red-300">{{ error }}</p>
    <template #footer>
      <button type="button" class="rounded-md border border-slate-200 px-3 py-1.5 text-sm text-slate-600 hover:bg-slate-50 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-800/60" @click="emit('close')">{{ $t('common.actions.cancel') }}</button>
      <button
        type="button"
        :disabled="submitting || list.length === 0"
        class="rounded-md bg-emerald-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-emerald-700 disabled:opacity-60"
        @click="onSave"
      >
        {{ submitting ? $t('common.state.saving') : $t('common.actions.save') }}
      </button>
    </template>
  </AppDialog>
</template>
