<script setup lang="ts">
import type { QaPriority } from '~/types/api'
import { ArrowDown, Minus, ArrowUp, Flame } from '@lucide/vue'

const props = defineProps<{ priority: QaPriority }>()

const { t } = useI18n()

const config: Record<QaPriority, { cls: string; icon: any }> = {
  low:      { cls: 'bg-slate-100 text-slate-600', icon: ArrowDown },
  medium:   { cls: 'bg-teal-50 text-teal-600',    icon: Minus },
  high:     { cls: 'bg-amber-50 text-amber-600',  icon: ArrowUp },
  critical: { cls: 'bg-rose-50 text-rose-600',    icon: Flame },
}

const view = computed(() => ({ ...config[props.priority], label: t(`common.priority.${props.priority}`) }))
</script>

<template>
  <span :class="['inline-flex items-center gap-1 whitespace-nowrap px-2 py-0.5 rounded-md text-xs font-medium', view.cls]">
    <component :is="view.icon" class="h-3 w-3" />
    {{ view.label }}
  </span>
</template>
