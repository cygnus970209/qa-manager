<script setup lang="ts">
import type { QaPriority } from '~/types/api'
import { ArrowDown, Minus, ArrowUp, Flame } from '@lucide/vue'

const props = defineProps<{ priority: QaPriority }>()

const config: Record<QaPriority, { label: string; cls: string; icon: any }> = {
  low:      { label: '낮음', cls: 'bg-slate-100 text-slate-600', icon: ArrowDown },
  medium:   { label: '보통', cls: 'bg-teal-50 text-teal-600',    icon: Minus },
  high:     { label: '높음', cls: 'bg-amber-50 text-amber-600',  icon: ArrowUp },
  critical: { label: '긴급', cls: 'bg-rose-50 text-rose-600',    icon: Flame },
}

const view = computed(() => config[props.priority])
</script>

<template>
  <span :class="['inline-flex items-center gap-1 px-2 py-0.5 rounded-md text-xs font-medium', view.cls]">
    <component :is="view.icon" class="h-3 w-3" />
    {{ view.label }}
  </span>
</template>
