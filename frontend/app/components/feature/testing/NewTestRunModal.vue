<script setup lang="ts">
import { Loader2 } from '@lucide/vue'
import AppDialog from '~/components/base/AppDialog.vue'
import PriorityBadge from '~/components/base/PriorityBadge.vue'
import type { TestCase, TestPlatform, TestRunDetail, TestSuite } from '~/types/api'

const props = defineProps<{
  open: boolean
  updateId: number
  projectId: number
  defaultName: string
}>()
const emit = defineEmits<{ close: []; created: [detail: TestRunDetail] }>()

const testingApi = useTesting()
const { t } = useI18n()

const name = ref('')
const suites = ref<TestSuite[]>([])
const cases = ref<TestCase[]>([])
const selected = ref(new Set<number>())
const loading = ref(false)
const submitting = ref(false)
const error = ref<string | null>(null)

/** 실행 플랫폼 다중 선택. 비어 있으면 플랫폼 구분 없는 공통 1회. */
const PLATFORMS: { value: TestPlatform; label: string }[] = [
  { value: 'PC', label: 'PC' },
  { value: 'ANDROID', label: 'Android' },
  { value: 'IOS', label: 'iOS' },
]
const selectedPlatforms = ref(new Set<TestPlatform>())
function togglePlatform(p: TestPlatform) {
  const next = new Set(selectedPlatforms.value)
  if (next.has(p)) next.delete(p)
  else next.add(p)
  selectedPlatforms.value = next
}
/** 생성될 실행 항목 수 = 선택 케이스 × (선택 플랫폼 수 || 1) */
const runItemCount = computed(() => selected.value.size * Math.max(selectedPlatforms.value.size, 1))

// 부모가 v-if 로 마운트와 동시에 open=true 를 넘기는 경우가 있어 immediate 로 초기 로드를 보장한다.
watch(() => props.open, async (v) => {
  if (!v) return
  name.value = props.defaultName
  selected.value = new Set()
  selectedPlatforms.value = new Set()
  error.value = null
  loading.value = true
  try {
    const [s, c] = await Promise.all([
      testingApi.listSuites(props.projectId),
      testingApi.listCases(props.projectId),
    ])
    suites.value = s
    cases.value = c
    // 기본값: 전체 선택 (런은 보통 전체 케이스 실행이 출발점)
    selected.value = new Set(c.map((x) => x.id))
  } catch (e: any) {
    error.value = e?.data?.message ?? t('testrun.modal.loadFailed')
  } finally {
    loading.value = false
  }
}, { immediate: true })

interface CaseGroup {
  key: string
  name: string
  cases: TestCase[]
}

/** 스위트별 그룹(미분류 포함). 케이스 없는 스위트는 표시하지 않는다. */
const groups = computed<CaseGroup[]>(() => {
  const bySuite = new Map<number, TestCase[]>()
  const ungrouped: TestCase[] = []
  for (const c of cases.value) {
    if (c.suiteId == null) {
      ungrouped.push(c)
      continue
    }
    const arr = bySuite.get(c.suiteId) ?? []
    arr.push(c)
    bySuite.set(c.suiteId, arr)
  }
  const list: CaseGroup[] = []
  for (const s of [...suites.value].sort((a, b) => a.sortOrder - b.sortOrder)) {
    const arr = bySuite.get(s.id)
    if (arr && arr.length > 0) list.push({ key: `s-${s.id}`, name: s.name, cases: arr })
  }
  if (ungrouped.length > 0) list.push({ key: 'ungrouped', name: t('testrun.modal.ungrouped'), cases: ungrouped })
  return list
})

const allSelected = computed(() => cases.value.length > 0 && selected.value.size === cases.value.length)

function toggleCase(id: number) {
  const next = new Set(selected.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  selected.value = next
}
function groupChecked(g: CaseGroup) {
  return g.cases.every((c) => selected.value.has(c.id))
}
function groupIndeterminate(g: CaseGroup) {
  return !groupChecked(g) && g.cases.some((c) => selected.value.has(c.id))
}
function toggleGroup(g: CaseGroup) {
  const next = new Set(selected.value)
  const all = groupChecked(g)
  for (const c of g.cases) {
    if (all) next.delete(c.id)
    else next.add(c.id)
  }
  selected.value = next
}
function toggleAll() {
  selected.value = allSelected.value ? new Set() : new Set(cases.value.map((c) => c.id))
}

const canSubmit = computed(() => name.value.trim() !== '' && selected.value.size > 0)

async function onSubmit() {
  if (submitting.value || !canSubmit.value) return
  submitting.value = true
  error.value = null
  try {
    // 그룹 표시 순서(스위트 정렬 → 미분류)대로 caseIds 를 구성해 런 내 정렬을 자연스럽게 유지
    const caseIds = groups.value.flatMap((g) => g.cases.filter((c) => selected.value.has(c.id)).map((c) => c.id))
    const platforms = PLATFORMS.filter((p) => selectedPlatforms.value.has(p.value)).map((p) => p.value)
    const detail = await testingApi.createRun(props.updateId, { name: name.value.trim(), caseIds, platforms })
    emit('created', detail)
    emit('close')
  } catch (e: any) {
    error.value = e?.data?.message ?? t('testrun.modal.createFailed')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <AppDialog :open="open" :title="$t('testrun.modal.title')" max-width="max-w-2xl" @close="emit('close')">
    <form id="new-testrun-form" class="space-y-4" @submit.prevent="onSubmit">
      <label class="block">
        <span class="block text-xs font-medium text-slate-600 dark:text-slate-300">{{ $t('testrun.modal.nameLabel') }}</span>
        <input
          v-model="name"
          type="text"
          required
          maxlength="200"
          :placeholder="$t('testrun.modal.namePlaceholder')"
          class="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100 dark:placeholder-slate-500"
        />
      </label>

      <!-- 실행 플랫폼 (다중 선택 = 케이스 × 플랫폼으로 실행 항목 확장) -->
      <div>
        <span class="block text-xs font-medium text-slate-600 dark:text-slate-300">{{ $t('testrun.modal.platformLabel') }}</span>
        <div class="mt-1.5 flex items-center gap-2">
          <button
            v-for="p in PLATFORMS"
            :key="p.value"
            type="button"
            :class="[
              'rounded-md border px-3 py-1.5 text-xs font-medium transition-colors',
              selectedPlatforms.has(p.value)
                ? 'border-emerald-500 bg-emerald-50 text-emerald-700 dark:border-emerald-500/60 dark:bg-emerald-500/10 dark:text-emerald-400'
                : 'border-slate-200 text-slate-500 hover:bg-slate-50 dark:border-slate-800 dark:text-slate-400 dark:hover:bg-slate-800/60',
            ]"
            @click="togglePlatform(p.value)"
          >
            {{ p.label }}
          </button>
          <span class="text-[11px] text-slate-400 dark:text-slate-500">{{ $t('testrun.modal.platformHint') }}</span>
        </div>
      </div>

      <div>
        <div class="flex items-center justify-between gap-2">
          <span class="text-xs font-medium text-slate-600 dark:text-slate-300">{{ $t('testrun.modal.casesLabel') }}</span>
          <div v-if="!loading && cases.length > 0" class="flex items-center gap-2 text-xs">
            <span class="tabular-nums text-slate-400 dark:text-slate-500">{{ $t('testrun.modal.selectedCount', selected.size) }}</span>
            <button
              type="button"
              class="font-medium text-emerald-600 hover:underline dark:text-emerald-400"
              @click="toggleAll"
            >
              {{ allSelected ? $t('testrun.modal.deselectAll') : $t('testrun.modal.selectAll') }}
            </button>
          </div>
        </div>

        <div v-if="loading" class="mt-2 flex items-center justify-center rounded-lg border border-slate-200 py-10 dark:border-slate-800">
          <Loader2 class="h-5 w-5 animate-spin text-slate-400 dark:text-slate-500" />
        </div>

        <div
          v-else-if="cases.length === 0"
          class="mt-2 rounded-lg border border-dashed border-slate-200 px-4 py-8 text-center text-xs text-slate-400 dark:border-slate-800 dark:text-slate-500"
        >
          {{ $t('testrun.modal.emptyCases') }}
        </div>

        <div v-else class="mt-2 max-h-72 overflow-y-auto rounded-lg border border-slate-200 dark:border-slate-800">
          <div v-for="g in groups" :key="g.key">
            <label class="flex cursor-pointer items-center gap-2 bg-slate-50 px-3 py-1.5 dark:bg-slate-800/50">
              <input
                type="checkbox"
                :checked="groupChecked(g)"
                :indeterminate="groupIndeterminate(g)"
                class="h-3.5 w-3.5 rounded border-slate-300 text-emerald-600 accent-emerald-600 focus:ring-emerald-500 dark:border-slate-600 dark:bg-slate-900"
                @change="toggleGroup(g)"
              />
              <span class="min-w-0 flex-1 truncate text-xs font-semibold text-slate-600 dark:text-slate-300">{{ g.name }}</span>
              <span class="tabular-nums text-[10px] text-slate-400 dark:text-slate-500">{{ g.cases.length }}</span>
            </label>
            <label
              v-for="c in g.cases"
              :key="c.id"
              class="flex cursor-pointer items-center gap-2 px-3 py-1.5 transition hover:bg-slate-50 dark:hover:bg-slate-800/60"
            >
              <input
                type="checkbox"
                :checked="selected.has(c.id)"
                class="h-3.5 w-3.5 rounded border-slate-300 text-emerald-600 accent-emerald-600 focus:ring-emerald-500 dark:border-slate-600 dark:bg-slate-900"
                @change="toggleCase(c.id)"
              />
              <span class="min-w-0 flex-1 truncate text-sm text-slate-700 dark:text-slate-200">{{ c.title }}</span>
              <PriorityBadge :priority="c.priority" />
            </label>
          </div>
        </div>
      </div>

      <p v-if="error" class="rounded bg-red-50 px-3 py-2 text-xs text-red-700 dark:bg-red-500/10 dark:text-red-400">{{ error }}</p>
    </form>

    <template #footer>
      <button
        type="button"
        class="rounded-md border border-slate-200 px-3 py-1.5 text-sm text-slate-600 hover:bg-slate-50 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-800/60"
        @click="emit('close')"
      >{{ $t('common.actions.cancel') }}</button>
      <span v-if="canSubmit" class="mr-auto self-center text-xs tabular-nums text-slate-400 dark:text-slate-500">
        {{ $t('testrun.modal.itemCount', runItemCount) }}
      </span>
      <button
        type="submit"
        form="new-testrun-form"
        :disabled="submitting || loading || !canSubmit"
        class="rounded-md bg-emerald-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-emerald-700 disabled:opacity-60"
      >
        {{ submitting ? $t('testrun.modal.creating') : $t('testrun.modal.create') }}
      </button>
    </template>
  </AppDialog>
</template>
