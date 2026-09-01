<script setup lang="ts">
import { Pin, PinOff } from '@lucide/vue'
import type { Project } from '~/types/api'
import StatusBadge from '~/components/base/StatusBadge.vue'

const props = defineProps<{
  project: Project
  qaCount: number
  resolvedCount: number
}>()

const emit = defineEmits<{ togglePin: [projectId: number] }>()

const progress = computed(() => (props.qaCount > 0
  ? Math.round((props.resolvedCount / props.qaCount) * 100)
  : 0))

function onPin(e: Event) {
  e.preventDefault()
  e.stopPropagation()
  emit('togglePin', props.project.id)
}
</script>

<template>
  <NuxtLink
    :to="`/project/${project.id}`"
    :class="[
      'relative block rounded-xl border bg-white p-5 transition hover:border-emerald-300 hover:shadow-sm',
      project.pinned ? 'border-emerald-300 ring-1 ring-emerald-100' : 'border-slate-200',
    ]"
  >
    <div class="mb-3 flex items-start justify-between gap-2">
      <div class="flex min-w-0 items-center gap-2">
        <div class="h-2 w-2 shrink-0 rounded-full bg-emerald-400" />
        <h3 class="truncate text-sm font-semibold text-slate-800">{{ project.name }}</h3>
      </div>
      <div class="flex shrink-0 items-center gap-2">
        <StatusBadge :status="project.status" />
        <button
          type="button"
          :class="[
            'flex h-7 w-7 items-center justify-center rounded-lg transition',
            project.pinned
              ? 'bg-emerald-100 text-emerald-600 hover:bg-emerald-200'
              : 'bg-slate-50 text-slate-400 hover:bg-slate-100 hover:text-slate-600',
          ]"
          :title="project.pinned ? $t('dashboard.projectCard.unpin') : $t('dashboard.projectCard.pin')"
          @click="onPin"
        >
          <component :is="project.pinned ? Pin : PinOff" class="h-3.5 w-3.5" />
        </button>
      </div>
    </div>
    <p class="mb-4 line-clamp-2 min-h-[2.5rem] text-xs text-slate-500">
      {{ project.description }}
    </p>
    <div class="flex items-center justify-between">
      <div class="flex items-center gap-3 text-xs text-slate-400">
        <span>{{ $t('dashboard.projectCard.total', { count: qaCount }) }}</span>
        <span>{{ $t('dashboard.projectCard.resolved', { count: resolvedCount }) }}</span>
      </div>
      <div class="flex items-center gap-1.5">
        <div class="h-1.5 w-16 overflow-hidden rounded-full bg-slate-100">
          <div class="h-full rounded-full bg-emerald-400 transition-all" :style="{ width: `${progress}%` }" />
        </div>
        <span class="text-xs font-medium text-slate-500">{{ progress }}%</span>
      </div>
    </div>
  </NuxtLink>
</template>
