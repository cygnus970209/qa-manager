<script setup lang="ts">
import { ArrowLeft } from '@lucide/vue'
import StatusBadge from '~/components/base/StatusBadge.vue'
import type { Project, ProjectStatus } from '~/types/api'

const props = defineProps<{
  project: Project
  totalQA: number
  resolvedCount: number
  updateCount: number
}>()
const emit = defineEmits<{
  changeStatus: [status: 'ACTIVE' | 'PAUSED' | 'COMPLETED']
}>()

const router = useRouter()

const statusOptions: { code: ProjectStatus; uppercase: 'ACTIVE' | 'PAUSED' | 'COMPLETED'; label: string }[] = [
  { code: 'active',    uppercase: 'ACTIVE',    label: '진행중' },
  { code: 'paused',    uppercase: 'PAUSED',    label: '일시중지' },
  { code: 'completed', uppercase: 'COMPLETED', label: '완료' },
]

const progress = computed(() => (props.totalQA > 0
  ? Math.round((props.resolvedCount / props.totalQA) * 100)
  : 0))

function onSelectStatus(e: Event) {
  const v = (e.target as HTMLSelectElement).value as 'ACTIVE' | 'PAUSED' | 'COMPLETED'
  emit('changeStatus', v)
}
</script>

<template>
  <header class="rounded-xl border border-slate-200 bg-white p-5 md:p-6">
    <button
      class="mb-3 inline-flex items-center gap-1 text-xs text-slate-500 hover:text-slate-900"
      type="button"
      @click="router.push('/')"
    >
      <ArrowLeft class="h-3.5 w-3.5" />
      대시보드
    </button>

    <div class="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
      <div class="min-w-0">
        <h1 class="truncate text-xl font-bold text-slate-800 md:text-2xl">{{ project.name }}</h1>
        <p class="mt-2 max-w-3xl text-sm text-slate-500">{{ project.description }}</p>
        <div class="mt-3 flex items-center gap-2 text-xs text-slate-400">
          <span>생성 {{ project.createdAt?.slice(0, 10) }}</span>
          <span>·</span>
          <span>업데이트 {{ updateCount }}개</span>
          <span>·</span>
          <span>QA {{ totalQA }}건 (완료 {{ resolvedCount }})</span>
        </div>
      </div>

      <div class="flex shrink-0 items-center gap-3">
        <StatusBadge :status="project.status" />
        <select
          class="rounded-md border border-slate-200 bg-white px-2 py-1.5 text-xs focus:outline-none focus:ring-2 focus:ring-emerald-200"
          :value="project.status === 'active' ? 'ACTIVE' : project.status === 'paused' ? 'PAUSED' : 'COMPLETED'"
          @change="onSelectStatus"
        >
          <option v-for="opt in statusOptions" :key="opt.uppercase" :value="opt.uppercase">
            상태: {{ opt.label }}
          </option>
        </select>
      </div>
    </div>

    <div class="mt-4 flex items-center gap-3">
      <div class="h-1.5 flex-1 overflow-hidden rounded-full bg-slate-100">
        <div class="h-full rounded-full bg-emerald-400 transition-all" :style="{ width: `${progress}%` }" />
      </div>
      <span class="text-xs font-medium text-slate-500">{{ progress }}%</span>
    </div>
  </header>
</template>
