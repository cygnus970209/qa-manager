<script setup lang="ts">
import { Trash2 } from '@lucide/vue'

defineProps<{
  open: boolean
  title?: string
  message?: string
  confirmLabel?: string
}>()
const emit = defineEmits<{ confirm: []; cancel: [] }>()

function onBackdrop(e: MouseEvent) {
  if (e.target === e.currentTarget) emit('cancel')
}
</script>

<template>
  <Teleport to="body">
    <div
      v-if="open"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4"
      @mousedown="onBackdrop"
    >
      <div class="w-full max-w-sm overflow-hidden rounded-xl bg-white shadow-xl" @mousedown.stop>
        <div class="px-5 pt-5 pb-2 text-center">
          <div class="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-red-50">
            <Trash2 class="h-5 w-5 text-red-500" />
          </div>
          <h3 class="text-base font-bold text-slate-800">{{ title ?? '정말 삭제하시겠습니까?' }}</h3>
          <p class="mt-1.5 text-sm text-slate-500">{{ message ?? '삭제하면 복구할 수 없습니다. 계속하시겠습니까?' }}</p>
        </div>
        <div class="flex gap-2 px-5 py-4">
          <button
            type="button"
            class="flex-1 rounded-lg bg-slate-100 px-4 py-2 text-sm font-medium text-slate-600 hover:bg-slate-200"
            @click="emit('cancel')"
          >취소</button>
          <button
            type="button"
            class="flex-1 rounded-lg bg-red-500 px-4 py-2 text-sm font-medium text-white hover:bg-red-600"
            @click="emit('confirm')"
          >{{ confirmLabel ?? '삭제' }}</button>
        </div>
      </div>
    </div>
  </Teleport>
</template>
