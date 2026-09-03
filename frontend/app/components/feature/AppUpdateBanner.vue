<script setup lang="ts">
import { RefreshCw, X } from '@lucide/vue'

/** 새 빌드가 배포됐을 때 화면 아래에 뜨는 안내. 새로고침을 누르면 현재 화면을 새 버전으로 다시 불러온다 */
const { available, reload } = useAppUpdate()
const dismissed = ref(false)
</script>

<template>
  <Transition
    enter-active-class="transition duration-200 ease-out"
    enter-from-class="translate-y-4 opacity-0"
    leave-active-class="transition duration-150 ease-in"
    leave-to-class="translate-y-4 opacity-0"
  >
    <div
      v-if="available && !dismissed"
      class="fixed bottom-5 left-1/2 z-50 flex -translate-x-1/2 items-center gap-3 rounded-xl border border-slate-200 bg-white px-4 py-3 shadow-lg dark:border-slate-700 dark:bg-slate-900"
      role="status"
    >
      <span class="text-sm text-slate-700 dark:text-slate-200">{{ $t('shell.update.available') }}</span>
      <button
        type="button"
        class="inline-flex items-center gap-1.5 rounded-lg bg-emerald-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-emerald-700"
        @click="reload"
      >
        <RefreshCw class="h-3.5 w-3.5" />
        {{ $t('shell.update.reload') }}
      </button>
      <button
        type="button"
        class="flex h-7 w-7 items-center justify-center rounded-md text-slate-400 hover:bg-slate-100 hover:text-slate-600 dark:hover:bg-slate-800 dark:hover:text-slate-200"
        :aria-label="$t('shell.update.later')"
        @click="dismissed = true"
      >
        <X class="h-4 w-4" />
      </button>
    </div>
  </Transition>
</template>
