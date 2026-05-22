<script setup lang="ts">
import { ChevronDown, Pencil, Plus, Trash2 } from '@lucide/vue'
import StatusBadge from '~/components/base/StatusBadge.vue'
import PriorityBadge from '~/components/base/PriorityBadge.vue'
import type { ProjectUpdate, QaItem, UpdateStatus } from '~/types/api'

const props = defineProps<{
  update: ProjectUpdate
  items: QaItem[]
  defaultOpen?: boolean
}>()
const emit = defineEmits<{
  changeStatus: [updateId: number, status: 'IN_PROGRESS' | 'TESTING' | 'RELEASED']
  addQa: [updateId: number]
  edit: [update: ProjectUpdate]
  remove: [update: ProjectUpdate]
}>()

const open = ref(!!props.defaultOpen)

const resolvedCount = computed(() => props.items.filter(
  (q) => q.status === 'resolved' || q.status === 'closed',
).length)

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
</script>

<template>
  <div class="rounded-xl border border-slate-200 bg-white">
    <button
      type="button"
      class="flex w-full items-center justify-between gap-3 px-5 py-3 text-left transition hover:bg-slate-50"
      @click="open = !open"
    >
      <div class="flex min-w-0 items-center gap-3">
        <ChevronDown
          :class="['h-4 w-4 text-slate-400 transition-transform', open && 'rotate-180']"
        />
        <span class="rounded bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-600">
          {{ update.version }}
        </span>
        <span class="truncate text-sm font-semibold text-slate-800">{{ update.title }}</span>
        <StatusBadge :status="update.status" />
      </div>
      <div class="flex shrink-0 items-center gap-2 text-xs text-slate-400">
        <span>QA {{ items.length }}건 (완료 {{ resolvedCount }})</span>
      </div>
    </button>

    <div v-if="open" class="border-t border-slate-100 px-5 py-4">
      <p v-if="update.description" class="mb-3 text-sm text-slate-500">{{ update.description }}</p>
      <div class="mb-3 flex items-center gap-2">
        <select
          class="rounded-md border border-slate-200 bg-white px-2 py-1 text-xs focus:outline-none focus:ring-2 focus:ring-emerald-200"
          :value="upperStatus"
          @change="onChangeStatus"
          @click.stop
        >
          <option value="IN_PROGRESS">상태: 진행중</option>
          <option value="TESTING">상태: 테스트</option>
          <option value="RELEASED">상태: 배포완료</option>
        </select>
        <button
          type="button"
          class="ml-auto inline-flex items-center gap-1 rounded-md bg-blue-500 px-2.5 py-1 text-xs font-medium text-white hover:bg-blue-600"
          @click.stop="emit('addQa', update.id)"
        >
          <Plus class="h-3.5 w-3.5" /> QA 추가
        </button>
        <button
          type="button"
          class="inline-flex h-7 w-7 items-center justify-center rounded-md border border-slate-200 text-slate-500 hover:bg-slate-50 hover:text-emerald-600"
          title="업데이트 수정"
          @click.stop="emit('edit', update)"
        >
          <Pencil class="h-3.5 w-3.5" />
        </button>
        <button
          type="button"
          class="inline-flex h-7 w-7 items-center justify-center rounded-md border border-slate-200 text-slate-500 hover:bg-red-50 hover:text-red-600"
          title="업데이트 삭제"
          @click.stop="emit('remove', update)"
        >
          <Trash2 class="h-3.5 w-3.5" />
        </button>
      </div>

      <ul v-if="items.length > 0" class="divide-y divide-slate-100 overflow-hidden rounded-lg border border-slate-200">
        <li
          v-for="q in items"
          :key="q.id"
          class="cursor-pointer px-4 py-3 transition hover:bg-slate-50"
          @click="$router.push(`/qa/${q.id}`)"
        >
          <div class="flex items-start justify-between gap-3">
            <div class="min-w-0">
              <p class="line-clamp-1 text-sm font-medium text-slate-800">{{ q.title }}</p>
              <p v-if="q.description" class="mt-0.5 line-clamp-1 text-xs text-slate-400">{{ q.description }}</p>
            </div>
            <div class="flex shrink-0 items-center gap-2">
              <StatusBadge :status="q.status" />
              <PriorityBadge :priority="q.priority" />
            </div>
          </div>
        </li>
      </ul>
      <div v-else class="rounded-lg border border-dashed border-slate-200 px-4 py-6 text-center text-xs text-slate-400">
        등록된 QA 항목이 없습니다.
      </div>
    </div>
  </div>
</template>
