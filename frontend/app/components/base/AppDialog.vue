<script setup lang="ts">
import { X } from '@lucide/vue'

const props = defineProps<{
  open: boolean
  title: string
  maxWidth?: string
}>()
const emit = defineEmits<{ close: [] }>()

function onBackdrop(e: MouseEvent) {
  if (e.target === e.currentTarget) emit('close')
}
</script>

<template>
  <Teleport to="body">
    <div
      v-if="open"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4"
      @mousedown="onBackdrop"
    >
      <div
        :class="['w-full rounded-2xl bg-white shadow-xl', maxWidth ?? 'max-w-lg']"
        @mousedown.stop
      >
        <header class="flex items-center justify-between border-b border-slate-100 px-5 py-3">
          <h2 class="text-sm font-semibold text-slate-800">{{ title }}</h2>
          <button
            type="button"
            class="rounded p-1 text-slate-400 hover:bg-slate-100 hover:text-slate-700"
            @click="emit('close')"
          >
            <X class="h-4 w-4" />
          </button>
        </header>
        <div class="max-h-[70vh] overflow-y-auto px-5 py-4">
          <slot />
        </div>
        <footer v-if="$slots.footer" class="flex justify-end gap-2 border-t border-slate-100 px-5 py-3">
          <slot name="footer" />
        </footer>
      </div>
    </div>
  </Teleport>
</template>
