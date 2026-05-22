<script setup lang="ts">
import type { QaHistoryEntry } from '~/types/api'
import { timeAgo } from '~/utils/format'

defineProps<{ entries: QaHistoryEntry[] }>()

const fieldLabels: Record<string, string> = {
  status: '상태',
  priority: '우선순위',
  assignee: '담당자',
  title: '제목',
  description: '설명',
  category: '카테고리',
}
</script>

<template>
  <section class="rounded-xl border border-slate-200 bg-white p-5">
    <h2 class="mb-3 text-sm font-semibold text-slate-700">변경 이력</h2>
    <ol v-if="entries.length > 0" class="space-y-3">
      <li v-for="h in entries" :key="h.id" class="flex gap-3">
        <div class="mt-1 h-2 w-2 shrink-0 rounded-full bg-emerald-400" />
        <div class="flex-1 text-sm">
          <p class="text-slate-700">
            <span class="font-medium">{{ h.changedBy?.name ?? '시스템' }}</span>
            님이 <span class="font-medium">{{ fieldLabels[h.field] ?? h.field }}</span>을(를)
            <span class="rounded bg-slate-100 px-1 py-0.5 text-xs">{{ h.oldValue ?? '—' }}</span>
            →
            <span class="rounded bg-emerald-50 px-1 py-0.5 text-xs text-emerald-700">{{ h.newValue ?? '—' }}</span>
            로 변경
          </p>
          <p class="mt-0.5 text-xs text-slate-400">{{ timeAgo(h.changedAt) }}</p>
        </div>
      </li>
    </ol>
    <p v-else class="text-xs text-slate-400">변경 이력이 없습니다.</p>
  </section>
</template>
