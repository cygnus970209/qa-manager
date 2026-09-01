<script setup lang="ts">
import { ChevronDown, ChevronUp, Plus, Trash2 } from '@lucide/vue'
import AppDialog from '~/components/base/AppDialog.vue'
import type { QaPriority, TestCase, TestStep, TestSuite } from '~/types/api'

const props = defineProps<{
  open: boolean
  mode: 'create' | 'edit'
  projectId: number
  suites: TestSuite[]
  testCase?: TestCase | null
  defaultSuiteId?: number | null
}>()
const emit = defineEmits<{ close: []; saved: [testCase: TestCase] }>()

const testing = useTesting()
const { t } = useI18n()

const form = reactive<{
  title: string
  suiteId: number | null
  priority: QaPriority
  precondition: string
  steps: TestStep[]
}>({
  title: '',
  suiteId: null,
  priority: 'medium',
  precondition: '',
  steps: [{ action: '', expected: '' }],
})

const submitting = ref(false)
const error = ref<string | null>(null)

/* open 시점에 폼 초기화 — edit 이면 기존 케이스, create 면 기본값 */
watch(() => props.open, (v) => {
  if (!v) return
  error.value = null
  if (props.mode === 'edit' && props.testCase) {
    form.title = props.testCase.title
    form.suiteId = props.testCase.suiteId
    form.priority = props.testCase.priority
    form.precondition = props.testCase.precondition ?? ''
    form.steps = props.testCase.steps.length > 0
      ? props.testCase.steps.map((s) => ({ action: s.action, expected: s.expected }))
      : [{ action: '', expected: '' }]
  } else {
    form.title = ''
    form.suiteId = props.defaultSuiteId ?? null
    form.priority = 'medium'
    form.precondition = ''
    form.steps = [{ action: '', expected: '' }]
  }
})

function addStep() {
  form.steps.push({ action: '', expected: '' })
}

function removeStep(idx: number) {
  if (form.steps.length <= 1) return // 최소 1행 유지
  form.steps.splice(idx, 1)
}

function moveStep(idx: number, dir: -1 | 1) {
  const to = idx + dir
  if (to < 0 || to >= form.steps.length) return
  const [row] = form.steps.splice(idx, 1)
  form.steps.splice(to, 0, row!)
}

async function onSubmit() {
  error.value = null
  // 빈 action 행은 저장에서 제외
  const steps = form.steps
    .filter((s) => s.action.trim() !== '')
    .map((s) => ({ action: s.action.trim(), expected: s.expected.trim() }))
  if (steps.length === 0) {
    error.value = t('testcase.modal.stepsRequired')
    return
  }
  submitting.value = true
  try {
    let saved: TestCase
    if (props.mode === 'edit' && props.testCase) {
      saved = await testing.updateCase(props.testCase.id, {
        suiteId: form.suiteId ?? 0, // 0 = 미분류(스위트 해제)
        title: form.title.trim(),
        precondition: form.precondition.trim(),
        steps,
        priority: form.priority,
      })
    } else {
      saved = await testing.createCase(props.projectId, {
        suiteId: form.suiteId,
        title: form.title.trim(),
        precondition: form.precondition.trim() || null,
        steps,
        priority: form.priority,
      })
    }
    emit('saved', saved)
    emit('close')
  } catch (e: any) {
    error.value = e?.data?.message ?? t('testcase.modal.saveFailed')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <AppDialog
    :open="open"
    :title="mode === 'edit' ? $t('testcase.modal.editTitle') : $t('testcase.modal.createTitle')"
    max-width="max-w-2xl"
    @close="emit('close')"
  >
    <form id="test-case-form" class="space-y-4" @submit.prevent="onSubmit">
      <label class="block">
        <span class="block text-xs font-medium text-slate-600 dark:text-slate-300">{{ $t('testcase.modal.titleLabel') }}</span>
        <input
          v-model="form.title"
          type="text"
          required
          maxlength="200"
          class="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100 dark:placeholder-slate-500"
        />
      </label>

      <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <label class="block">
          <span class="block text-xs font-medium text-slate-600 dark:text-slate-300">{{ $t('testcase.modal.suiteLabel') }}</span>
          <select
            v-model="form.suiteId"
            class="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100"
          >
            <option :value="null">{{ $t('testcase.suite.unsorted') }}</option>
            <option v-for="s in suites" :key="s.id" :value="s.id">{{ s.name }}</option>
          </select>
        </label>
        <label class="block">
          <span class="block text-xs font-medium text-slate-600 dark:text-slate-300">{{ $t('testcase.modal.priorityLabel') }}</span>
          <select
            v-model="form.priority"
            class="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100"
          >
            <option value="low">{{ $t('common.priority.low') }}</option>
            <option value="medium">{{ $t('common.priority.medium') }}</option>
            <option value="high">{{ $t('common.priority.high') }}</option>
            <option value="critical">{{ $t('common.priority.critical') }}</option>
          </select>
        </label>
      </div>

      <label class="block">
        <span class="block text-xs font-medium text-slate-600 dark:text-slate-300">{{ $t('testcase.modal.preconditionLabel') }}</span>
        <textarea
          v-model="form.precondition"
          rows="2"
          maxlength="2000"
          :placeholder="$t('testcase.modal.preconditionPlaceholder')"
          class="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100 dark:placeholder-slate-500"
        />
      </label>

      <!-- 스텝 편집기 -->
      <div>
        <span class="block text-xs font-medium text-slate-600 dark:text-slate-300">{{ $t('testcase.modal.stepsLabel') }}</span>
        <div class="mt-1 space-y-2">
          <div v-for="(step, i) in form.steps" :key="i" class="flex items-start gap-2">
            <span class="mt-2 w-5 shrink-0 text-right text-xs tabular-nums text-slate-400 dark:text-slate-500">{{ i + 1 }}</span>
            <input
              v-model="step.action"
              type="text"
              maxlength="500"
              :placeholder="$t('testcase.modal.actionPlaceholder')"
              class="min-w-0 flex-1 rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100 dark:placeholder-slate-500"
            />
            <input
              v-model="step.expected"
              type="text"
              maxlength="500"
              :placeholder="$t('testcase.modal.expectedPlaceholder')"
              class="min-w-0 flex-1 rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100 dark:placeholder-slate-500"
            />
            <div class="flex shrink-0 items-center gap-0.5 pt-1">
              <button
                type="button"
                :disabled="i === 0"
                :title="$t('testcase.modal.moveUp')"
                class="rounded p-1 text-slate-400 hover:bg-slate-100 hover:text-slate-700 disabled:opacity-30 disabled:hover:bg-transparent dark:text-slate-500 dark:hover:bg-slate-800 dark:hover:text-slate-300"
                @click="moveStep(i, -1)"
              >
                <ChevronUp class="h-4 w-4" />
              </button>
              <button
                type="button"
                :disabled="i === form.steps.length - 1"
                :title="$t('testcase.modal.moveDown')"
                class="rounded p-1 text-slate-400 hover:bg-slate-100 hover:text-slate-700 disabled:opacity-30 disabled:hover:bg-transparent dark:text-slate-500 dark:hover:bg-slate-800 dark:hover:text-slate-300"
                @click="moveStep(i, 1)"
              >
                <ChevronDown class="h-4 w-4" />
              </button>
              <button
                type="button"
                :disabled="form.steps.length <= 1"
                :title="$t('common.actions.delete')"
                class="rounded p-1 text-slate-400 hover:bg-red-50 hover:text-red-600 disabled:opacity-30 disabled:hover:bg-transparent disabled:hover:text-slate-400 dark:text-slate-500 dark:hover:bg-red-500/10 dark:hover:text-red-400 dark:disabled:hover:text-slate-500"
                @click="removeStep(i)"
              >
                <Trash2 class="h-4 w-4" />
              </button>
            </div>
          </div>
        </div>
        <button
          type="button"
          class="mt-2 inline-flex items-center gap-1 rounded-md border border-dashed border-slate-300 px-2.5 py-1.5 text-xs font-medium text-slate-500 hover:border-emerald-300 hover:text-emerald-600 dark:border-slate-700 dark:text-slate-400 dark:hover:border-emerald-500/50 dark:hover:text-emerald-400"
          @click="addStep"
        >
          <Plus class="h-3.5 w-3.5" /> {{ $t('testcase.modal.addStep') }}
        </button>
      </div>

      <p v-if="error" class="rounded bg-red-50 px-3 py-2 text-xs text-red-700 dark:bg-red-500/10 dark:text-red-400">{{ error }}</p>
    </form>

    <template #footer>
      <button
        type="button"
        class="rounded-md border border-slate-200 px-3 py-1.5 text-sm text-slate-600 hover:bg-slate-50 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-800/60"
        @click="emit('close')"
      >{{ $t('common.actions.cancel') }}</button>
      <button
        type="submit"
        form="test-case-form"
        :disabled="submitting"
        class="rounded-md bg-emerald-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-emerald-700 disabled:opacity-60"
      >
        {{ submitting ? $t('common.state.saving') : (mode === 'edit' ? $t('common.actions.save') : $t('common.actions.create')) }}
      </button>
    </template>
  </AppDialog>
</template>
