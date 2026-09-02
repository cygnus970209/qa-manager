<script setup lang="ts">
import { CircleHelp, TriangleAlert, Info } from '@lucide/vue'

/**
 * useAppDialog 의 표시 담당 싱글톤 — app.vue 에서 1회 마운트.
 * window.confirm/alert 대체 (임베디드 웹뷰 호환). z-index 는 AppDialog(z-50)보다 위.
 */
const { dialogState: s, resolveDialog } = useAppDialog()

function onKeydown(e: KeyboardEvent) {
  if (!s.open) return
  if (e.key === 'Escape') {
    e.preventDefault()
    resolveDialog(false)
  } else if (e.key === 'Enter') {
    e.preventDefault()
    resolveDialog(true)
  }
}
onMounted(() => window.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))

function onBackdrop(e: MouseEvent) {
  if (e.target === e.currentTarget) resolveDialog(false)
}
</script>

<template>
  <Teleport to="body">
    <div
      v-if="s.open"
      class="fixed inset-0 z-[70] flex items-center justify-center bg-black/40 px-4"
      @mousedown="onBackdrop"
    >
      <div class="w-full max-w-sm overflow-hidden rounded-xl bg-white shadow-xl dark:bg-slate-900" @mousedown.stop>
        <div class="px-5 pt-5 pb-2 text-center">
          <div
            :class="[
              'mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-full',
              s.danger ? 'bg-red-50 dark:bg-red-500/10' : 'bg-slate-100 dark:bg-slate-800',
            ]"
          >
            <TriangleAlert v-if="s.danger" class="h-5 w-5 text-red-500 dark:text-red-400" />
            <Info v-else-if="s.mode === 'alert'" class="h-5 w-5 text-slate-500 dark:text-slate-400" />
            <CircleHelp v-else class="h-5 w-5 text-slate-500 dark:text-slate-400" />
          </div>
          <h3 v-if="s.title" class="text-base font-bold text-slate-800 dark:text-slate-100">{{ s.title }}</h3>
          <p class="mt-1.5 whitespace-pre-line text-sm text-slate-600 dark:text-slate-300">{{ s.message }}</p>
        </div>
        <div class="flex gap-2 px-5 py-4">
          <button
            v-if="s.mode === 'confirm'"
            type="button"
            class="flex-1 rounded-lg bg-slate-100 px-4 py-2 text-sm font-medium text-slate-600 hover:bg-slate-200 dark:bg-slate-800 dark:text-slate-300 dark:hover:bg-slate-700"
            @click="resolveDialog(false)"
          >{{ s.cancelLabel || $t('common.actions.cancel') }}</button>
          <button
            type="button"
            :class="[
              'flex-1 rounded-lg px-4 py-2 text-sm font-medium text-white',
              s.danger ? 'bg-red-500 hover:bg-red-600' : 'bg-emerald-600 hover:bg-emerald-700',
            ]"
            @click="resolveDialog(true)"
          >{{ s.confirmLabel || $t('common.actions.confirm') }}</button>
        </div>
      </div>
    </div>
  </Teleport>
</template>
