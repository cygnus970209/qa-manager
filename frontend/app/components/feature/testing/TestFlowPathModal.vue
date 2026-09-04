<script setup lang="ts">
import { TriangleAlert, Route } from '@lucide/vue'
import AppDialog from '~/components/base/AppDialog.vue'
import AppSelect from '~/components/base/AppSelect.vue'
import type { SelectOption } from '~/composables/useSelectOptions'
import type { FlowPath } from '~/utils/flowPaths'
import type { FlowGraph, ProjectUpdate, TestSuite } from '~/types/api'

/**
 * 플로우 그래프의 시작→종료 경로를 열거해 테스트 케이스로 일괄 생성하는 모달.
 * 경로 열거는 enumerateFlowPaths(자동 임포트) 사용 — MAX_PATHS 초과 시 truncated 경고.
 */
const props = defineProps<{
  open: boolean
  projectId: number
  flowId: number
  flowName: string
  updateId: number | null
  updates: ProjectUpdate[]
  graph: FlowGraph
  suites: TestSuite[]
}>()
const emit = defineEmits<{ close: []; created: [count: number] }>()

const testing = useTesting()
const { t } = useI18n()

const result = computed(() => enumerateFlowPaths(props.graph))
const paths = computed(() => result.value.paths)
const maxPaths = MAX_PATHS

const checked = ref<boolean[]>([])
const suiteSel = ref<number | 'new'>('new')
const suiteOptions = computed<SelectOption<number | 'new'>[]>(() => [
  ...props.suites.map((s) => ({ value: s.id, label: s.name })),
  { value: 'new', label: t('testflow.pathModal.newSuiteOption') },
])
const newSuiteName = ref('')
const creating = ref(false)
const error = ref<string | null>(null)

/** 기본 제안 스위트 이름: 연결 업데이트가 있으면 "v버전 제목", 없으면 플로우 이름 */
const suggestedSuiteName = computed(() => {
  const u = props.updateId != null ? props.updates.find((x) => x.id === props.updateId) : undefined
  return u ? `${u.version} ${u.title}` : props.flowName
})

watch(() => props.open, (v) => {
  if (!v) return
  checked.value = paths.value.map(() => true) // 기본 전체 선택
  suiteSel.value = 'new'
  newSuiteName.value = suggestedSuiteName.value.slice(0, 100)
  error.value = null
}, { immediate: true })

const selectedCount = computed(() => checked.value.filter(Boolean).length)
const allChecked = computed(() => paths.value.length > 0 && checked.value.every(Boolean))

function toggleAll() {
  const v = !allChecked.value
  checked.value = paths.value.map(() => v)
}

/** 자동 제목이 비어있는 경로(시작→종료 직결 등) 폴백 제목 */
function pathTitle(p: FlowPath, index: number): string {
  return p.title || t('testflow.pathModal.untitledPath', { name: props.flowName, index: index + 1 })
}

function stepCountLabel(n: number): string {
  return t('testflow.pathModal.stepCount', { count: n }, n)
}

const summaryText = computed(() =>
  t('testflow.pathModal.summary', { name: props.flowName, count: paths.value.length }, paths.value.length))
const createLabel = computed(() =>
  t('testflow.pathModal.createButton', { count: selectedCount.value }, selectedCount.value))

const canSubmit = computed(() =>
  selectedCount.value > 0
  && !creating.value
  && (suiteSel.value !== 'new' || newSuiteName.value.trim().length > 0))

async function onSubmit() {
  if (!canSubmit.value) return
  creating.value = true
  error.value = null
  try {
    // 새 스위트 선택 시 먼저 생성
    let suiteId: number
    if (suiteSel.value === 'new') {
      const s = await testing.createSuite(props.projectId, newSuiteName.value.trim())
      suiteId = s.id
    } else {
      suiteId = suiteSel.value
    }
    const cases = paths.value
      .map((p, i) => ({ p, i }))
      .filter(({ i }) => checked.value[i])
      .map(({ p, i }) => ({
        title: pathTitle(p, i).slice(0, 200),
        steps: p.steps,
        priority: 'medium' as const,
      }))
    const created = await testing.bulkCreateCases(props.projectId, { suiteId, flowId: props.flowId, cases })
    emit('created', created.length)
    emit('close')
  } catch (e: any) {
    error.value = e?.data?.message ?? t('testflow.pathModal.createFailed')
  } finally {
    creating.value = false
  }
}
</script>

<template>
  <AppDialog :open="open" :title="$t('testflow.pathModal.title')" max-width="max-w-2xl" @close="emit('close')">
    <div class="space-y-4">
      <div class="flex flex-wrap items-center justify-between gap-2">
        <p class="flex items-center gap-1.5 text-xs text-slate-500 dark:text-slate-400">
          <Route class="h-3.5 w-3.5" />{{ summaryText }}
        </p>
        <label v-if="paths.length > 0" class="flex cursor-pointer select-none items-center gap-1.5 text-xs text-slate-600 dark:text-slate-300">
          <input
            type="checkbox"
            :checked="allChecked"
            class="h-4 w-4 rounded border-slate-300 accent-emerald-600 dark:border-slate-600 dark:bg-slate-900"
            @change="toggleAll"
          />
          {{ $t('testflow.pathModal.selectAll') }}
        </label>
      </div>

      <!-- MAX_PATHS 초과 경고 -->
      <div
        v-if="result.truncated"
        class="flex items-start gap-2 rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-700 dark:border-amber-500/40 dark:bg-amber-500/10 dark:text-amber-300"
      >
        <TriangleAlert class="mt-0.5 h-3.5 w-3.5 shrink-0" />
        <span>{{ $t('testflow.pathModal.truncatedWarning', { max: maxPaths }) }}</span>
      </div>

      <!-- 경로 없음 안내 -->
      <div
        v-if="paths.length === 0"
        class="rounded-md border border-slate-200 bg-slate-50 px-3 py-6 text-center text-xs text-slate-500 dark:border-slate-800 dark:bg-slate-800/50 dark:text-slate-400"
      >
        {{ $t('testflow.pathModal.noPaths') }}
      </div>

      <!-- 경로 목록 -->
      <ul
        v-else
        class="max-h-72 divide-y divide-slate-100 overflow-y-auto rounded-lg border border-slate-200 dark:divide-slate-800 dark:border-slate-800"
      >
        <li v-for="(p, i) in paths" :key="i">
          <label class="flex cursor-pointer items-center gap-2.5 px-3 py-2 hover:bg-slate-50 dark:hover:bg-slate-800/60">
            <input
              v-model="checked[i]"
              type="checkbox"
              class="h-4 w-4 shrink-0 rounded border-slate-300 accent-emerald-600 dark:border-slate-600 dark:bg-slate-900"
            />
            <span class="min-w-0 flex-1 truncate text-xs font-medium text-slate-700 dark:text-slate-200" :title="pathTitle(p, i)">
              {{ pathTitle(p, i) }}
            </span>
            <span class="shrink-0 rounded bg-slate-100 px-1.5 py-0.5 text-[10px] text-slate-500 dark:bg-slate-800 dark:text-slate-400">
              {{ stepCountLabel(p.steps.length) }}
            </span>
          </label>
        </li>
      </ul>

      <!-- 대상 스위트 -->
      <div>
        <span class="block text-xs font-medium text-slate-600 dark:text-slate-300">{{ $t('testflow.pathModal.targetSuite') }}</span>
        <AppSelect v-model="suiteSel" class="mt-1" size="md" :options="suiteOptions" />
        <input
          v-if="suiteSel === 'new'"
          v-model="newSuiteName"
          type="text"
          maxlength="100"
          :placeholder="$t('testflow.pathModal.newSuiteNamePlaceholder')"
          class="mt-2 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100 dark:placeholder-slate-500"
        />
      </div>

      <p v-if="error" class="rounded bg-red-50 px-3 py-2 text-xs text-red-700 dark:bg-red-500/10 dark:text-red-400">{{ error }}</p>
    </div>

    <template #footer>
      <button
        type="button"
        class="rounded-md border border-slate-200 px-3 py-1.5 text-sm text-slate-600 hover:bg-slate-50 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-800/60"
        @click="emit('close')"
      >{{ $t('common.actions.cancel') }}</button>
      <button
        type="button"
        :disabled="!canSubmit"
        class="rounded-md bg-emerald-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-emerald-700 disabled:opacity-60"
        @click="onSubmit"
      >
        {{ creating ? $t('testflow.pathModal.creating') : createLabel }}
      </button>
    </template>
  </AppDialog>
</template>
