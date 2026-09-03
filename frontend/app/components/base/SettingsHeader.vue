<script setup lang="ts">
/** 설정 화면 제목 + 한 줄 설명 */
defineProps<{ title: string; subtitle?: string }>()
</script>

<template>
  <header class="mb-6">
    <h1 class="text-xl font-bold text-slate-800 dark:text-slate-100">{{ title }}</h1>
    <p v-if="subtitle" class="mt-1 text-sm text-slate-500 dark:text-slate-400">{{ subtitle }}</p>
  </header>
</template>
