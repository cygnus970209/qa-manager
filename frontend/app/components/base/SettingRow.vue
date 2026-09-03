<script setup lang="ts">
import type { FunctionalComponent } from 'vue'

/** 설정 항목 한 줄: 아이콘 + 제목/설명 + 오른쪽 컨트롤(slot). 아래 확장 영역은 default slot */
defineProps<{
  icon: FunctionalComponent | any
  title: string
  description?: string
}>()
</script>

<template>
  <div class="rounded-xl border border-slate-100 bg-slate-50 p-4 transition-colors dark:border-slate-800 dark:bg-slate-800/50">
    <div class="flex items-center justify-between gap-4">
      <div class="flex min-w-0 items-start gap-3">
        <div class="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg border border-slate-100 bg-white text-slate-500 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-400">
          <component :is="icon" class="h-4 w-4" />
        </div>
        <div class="min-w-0">
          <p class="text-sm font-semibold text-slate-800 dark:text-slate-100">{{ title }}</p>
          <p v-if="description" class="mt-0.5 text-xs text-slate-500 dark:text-slate-400">{{ description }}</p>
        </div>
      </div>
      <div class="flex shrink-0 items-center gap-2">
        <slot name="control" />
      </div>
    </div>
    <slot />
  </div>
</template>
