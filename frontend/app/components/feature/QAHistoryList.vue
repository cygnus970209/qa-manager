<script setup lang="ts">
import type { QaHistoryEntry } from '~/types/api'
import { timeAgo } from '~/utils/format'

defineProps<{ entries: QaHistoryEntry[] }>()

const { t } = useI18n()

const fieldLabelKeys: Record<string, string> = {
  status: 'qa.fields.status',
  priority: 'qa.fields.priority',
  tester: 'common.roles.tester',
  assignee1: 'common.roles.assignee1',
  assignee2: 'common.roles.assignee2',
  title: 'qa.fields.title',
  description: 'qa.fields.description',
  category: 'qa.fields.category',
  update: 'qa.fields.update',
  image_added: 'qa.fields.image',
  image_removed: 'qa.fields.image',
}

function fieldLabel(field: string): string {
  const key = fieldLabelKeys[field]
  return key ? t(key) : field
}

// status / priority 의 oldValue·newValue 는 영어 code 로 저장되므로 표시 시 현재 언어로 변환.
const STATUS_VALUES = new Set(['needs_fix', 'in_progress', 'fix_done', 'confirmed', 'on_hold', 'needs_recheck'])
const PRIORITY_VALUES = new Set(['low', 'medium', 'high', 'critical'])

function displayValue(field: string, value: string | null | undefined): string {
  if (value == null || value === '') return '—'
  if (field === 'status' && STATUS_VALUES.has(value)) return t(`common.qaStatus.${value}`)
  if (field === 'priority' && PRIORITY_VALUES.has(value)) return t(`common.priority.${value}`)
  return value
}
</script>

<template>
  <section class="rounded-xl border border-slate-200 bg-white p-5">
    <h2 class="mb-3 text-sm font-semibold text-slate-700">{{ $t('qa.history.title') }}</h2>
    <ol v-if="entries.length > 0" class="space-y-3">
      <li v-for="h in entries" :key="h.id" class="flex gap-3">
        <div class="mt-1 h-2 w-2 shrink-0 rounded-full bg-emerald-400" />
        <div class="flex-1 text-sm">
          <p v-if="h.field === 'image_added'" class="text-slate-700">
            <span class="font-medium">{{ h.changedBy?.name ?? $t('qa.history.system') }}</span>
            {{ $t('qa.history.addedImage') }}
            <a v-if="h.newValue" :href="h.newValue" target="_blank" rel="noopener noreferrer" class="ml-1 inline-block align-middle">
              <img :src="h.newValue" :alt="$t('qa.history.addedImageAlt')" class="h-10 w-10 rounded border border-slate-200 object-cover" />
            </a>
          </p>
          <p v-else-if="h.field === 'image_removed'" class="text-slate-700">
            <span class="font-medium">{{ h.changedBy?.name ?? $t('qa.history.system') }}</span>
            {{ $t('qa.history.removedImage') }}
            <a v-if="h.oldValue" :href="h.oldValue" target="_blank" rel="noopener noreferrer" class="ml-1 inline-block align-middle">
              <img :src="h.oldValue" :alt="$t('qa.history.removedImageAlt')" class="h-10 w-10 rounded border border-slate-200 object-cover opacity-60 grayscale" />
            </a>
          </p>
          <div v-else class="text-slate-700">
            <p>
              <span class="font-medium">{{ h.changedBy?.name ?? $t('qa.history.system') }}</span>
              {{ $t('qa.history.changedBy') }} <span class="font-medium">{{ fieldLabel(h.field) }}</span>{{ $t('qa.history.fieldObjectParticle') }}
            </p>
            <p class="mt-1 flex flex-wrap items-center gap-1">
              <span class="rounded bg-slate-100 px-1 py-0.5 text-xs">{{ displayValue(h.field, h.oldValue) }}</span>
              <span class="text-slate-400">→</span>
              <span class="rounded bg-emerald-50 px-1 py-0.5 text-xs text-emerald-700">{{ displayValue(h.field, h.newValue) }}</span>
              <span>{{ $t('qa.history.changedTo') }}</span>
            </p>
          </div>
          <p class="mt-0.5 text-xs text-slate-400">{{ timeAgo(h.changedAt) }}</p>
        </div>
      </li>
    </ol>
    <p v-else class="text-xs text-slate-400">{{ $t('qa.history.empty') }}</p>
  </section>
</template>
