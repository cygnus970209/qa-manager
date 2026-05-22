<script setup lang="ts">
import type { FunctionalComponent } from 'vue'

defineProps<{
  title: string
  value: number
  icon: FunctionalComponent | any
  iconColor?: string
  iconBg?: string
  trend?: string
}>()
</script>

<template>
  <div class="rounded-xl border border-slate-200 bg-white p-5 transition hover:shadow-sm">
    <div class="flex items-start justify-between">
      <div>
        <p class="text-sm text-slate-500">{{ title }}</p>
        <p class="mt-1 text-2xl font-bold text-slate-800">{{ value.toLocaleString() }}</p>
        <p v-if="trend" class="mt-1 text-xs text-slate-400">{{ trend }}</p>
      </div>
      <div :class="['flex h-10 w-10 items-center justify-center rounded-lg', iconBg ?? 'bg-emerald-50']">
        <component :is="icon" :class="['h-5 w-5', iconColor ?? 'text-emerald-500']" />
      </div>
    </div>
  </div>
</template>
