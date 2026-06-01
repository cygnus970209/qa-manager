<script setup lang="ts">
import type { QaHistoryEntry } from '~/types/api'
import { timeAgo } from '~/utils/format'

defineProps<{ entries: QaHistoryEntry[] }>()

const fieldLabels: Record<string, string> = {
  status: '상태',
  priority: '우선순위',
  tester: '테스터',
  assignee1: '담당자1',
  assignee2: '담당자2',
  title: '제목',
  description: '설명',
  category: '카테고리',
  image_added: '이미지',
  image_removed: '이미지',
}

// status / priority 의 oldValue·newValue 는 영어 code 로 저장되므로 표시 시 한글로 변환.
const statusValueLabels: Record<string, string> = {
  needs_fix: '수정필요',
  in_progress: '진행중',
  fix_done: '수정완료',
  confirmed: '확인완료',
  on_hold: '보류',
  needs_recheck: '추가확인필요',
}
const priorityValueLabels: Record<string, string> = {
  low: '낮음',
  medium: '보통',
  high: '높음',
  critical: '긴급',
}

function displayValue(field: string, value: string | null | undefined): string {
  if (value == null || value === '') return '—'
  if (field === 'status') return statusValueLabels[value] ?? value
  if (field === 'priority') return priorityValueLabels[value] ?? value
  return value
}
</script>

<template>
  <section class="rounded-xl border border-slate-200 bg-white p-5">
    <h2 class="mb-3 text-sm font-semibold text-slate-700">변경 이력</h2>
    <ol v-if="entries.length > 0" class="space-y-3">
      <li v-for="h in entries" :key="h.id" class="flex gap-3">
        <div class="mt-1 h-2 w-2 shrink-0 rounded-full bg-emerald-400" />
        <div class="flex-1 text-sm">
          <p v-if="h.field === 'image_added'" class="text-slate-700">
            <span class="font-medium">{{ h.changedBy?.name ?? '시스템' }}</span>
            님이 이미지를 추가
            <a v-if="h.newValue" :href="h.newValue" target="_blank" rel="noopener noreferrer" class="ml-1 inline-block align-middle">
              <img :src="h.newValue" alt="추가된 이미지" class="h-10 w-10 rounded border border-slate-200 object-cover" />
            </a>
          </p>
          <p v-else-if="h.field === 'image_removed'" class="text-slate-700">
            <span class="font-medium">{{ h.changedBy?.name ?? '시스템' }}</span>
            님이 이미지를 삭제
            <a v-if="h.oldValue" :href="h.oldValue" target="_blank" rel="noopener noreferrer" class="ml-1 inline-block align-middle">
              <img :src="h.oldValue" alt="삭제된 이미지" class="h-10 w-10 rounded border border-slate-200 object-cover opacity-60 grayscale" />
            </a>
          </p>
          <div v-else class="text-slate-700">
            <p>
              <span class="font-medium">{{ h.changedBy?.name ?? '시스템' }}</span>
              님이 <span class="font-medium">{{ fieldLabels[h.field] ?? h.field }}</span>을(를)
            </p>
            <p class="mt-1 flex flex-wrap items-center gap-1">
              <span class="rounded bg-slate-100 px-1 py-0.5 text-xs">{{ displayValue(h.field, h.oldValue) }}</span>
              <span class="text-slate-400">→</span>
              <span class="rounded bg-emerald-50 px-1 py-0.5 text-xs text-emerald-700">{{ displayValue(h.field, h.newValue) }}</span>
              <span>로 변경</span>
            </p>
          </div>
          <p class="mt-0.5 text-xs text-slate-400">{{ timeAgo(h.changedAt) }}</p>
        </div>
      </li>
    </ol>
    <p v-else class="text-xs text-slate-400">변경 이력이 없습니다.</p>
  </section>
</template>
