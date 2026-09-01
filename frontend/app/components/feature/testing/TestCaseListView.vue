<script setup lang="ts">
import { ClipboardList, Pencil, Plus, Trash2 } from '@lucide/vue'
import PriorityBadge from '~/components/base/PriorityBadge.vue'
import DeleteConfirmModal from '~/components/base/DeleteConfirmModal.vue'
import TestCaseEditModal from '~/components/feature/testing/TestCaseEditModal.vue'
import { formatDate } from '~/utils/format'
import type { TestCase, TestSuite } from '~/types/api'

const props = defineProps<{ projectId: number }>()

const testing = useTesting()

const suites = ref<TestSuite[]>([])
const cases = ref<TestCase[]>([])
const loading = ref(false)
const loadError = ref(false)

/* 사이드바 선택 — 'all' 전체 / 'none' 미분류 / number 스위트 id */
const selected = ref<'all' | 'none' | number>('all')

async function load() {
  loading.value = true
  loadError.value = false
  try {
    const [s, c] = await Promise.all([
      testing.listSuites(props.projectId),
      testing.listCases(props.projectId),
    ])
    suites.value = s
    cases.value = c
  } catch (e) {
    console.error('TestCaseListView load failed', e)
    loadError.value = true
  } finally {
    loading.value = false
  }
}
onMounted(load)

const sortedSuites = computed(() => [...suites.value].sort((a, b) => a.sortOrder - b.sortOrder))
const unsortedCount = computed(() => cases.value.filter((c) => c.suiteId == null).length)

function suiteCount(id: number) {
  return cases.value.filter((c) => c.suiteId === id).length
}

const filtered = computed(() => {
  if (selected.value === 'all') return cases.value
  if (selected.value === 'none') return cases.value.filter((c) => c.suiteId == null)
  return cases.value.filter((c) => c.suiteId === selected.value)
})

/** 동적 인라인 입력 자동 포커스용 함수 ref */
function focusOnMount(el: unknown) {
  ;(el as HTMLInputElement | null)?.focus()
}

/* ── 스위트 추가 (인라인 입력) ── */
const addingSuite = ref(false)
const newSuiteName = ref('')
const suiteBusy = ref(false)

async function submitNewSuite() {
  const name = newSuiteName.value.trim()
  if (name === '' || suiteBusy.value) return
  suiteBusy.value = true
  try {
    const created = await testing.createSuite(props.projectId, name)
    suites.value.push(created)
    addingSuite.value = false
    newSuiteName.value = ''
  } catch (e) {
    console.error('createSuite failed', e)
  } finally {
    suiteBusy.value = false
  }
}

/* ── 스위트 이름 변경 (인라인 입력) ── */
const renamingId = ref<number | null>(null)
const renameName = ref('')

function startRename(s: TestSuite) {
  renamingId.value = s.id
  renameName.value = s.name
}

async function submitRename() {
  const id = renamingId.value
  const name = renameName.value.trim()
  if (id == null) return
  if (name === '' || suiteBusy.value) { renamingId.value = null; return }
  suiteBusy.value = true
  try {
    const updated = await testing.updateSuite(id, { name })
    const i = suites.value.findIndex((s) => s.id === id)
    if (i >= 0) suites.value[i] = updated
    renamingId.value = null
  } catch (e) {
    console.error('updateSuite failed', e)
  } finally {
    suiteBusy.value = false
  }
}

/* ── 스위트 삭제 ── */
const deletingSuite = ref<TestSuite | null>(null)

async function confirmDeleteSuite() {
  const target = deletingSuite.value
  if (!target) return
  try {
    await testing.removeSuite(target.id)
    suites.value = suites.value.filter((s) => s.id !== target.id)
    // 소속 케이스는 미분류로 이동
    cases.value = cases.value.map((c) => (c.suiteId === target.id ? { ...c, suiteId: null } : c))
    if (selected.value === target.id) selected.value = 'none'
  } catch (e) {
    console.error('removeSuite failed', e)
  } finally {
    deletingSuite.value = null
  }
}

/* ── 케이스 삭제 ── */
const deletingCase = ref<TestCase | null>(null)

async function confirmDeleteCase() {
  const target = deletingCase.value
  if (!target) return
  try {
    await testing.removeCase(target.id)
    cases.value = cases.value.filter((c) => c.id !== target.id)
  } catch (e) {
    console.error('removeCase failed', e)
  } finally {
    deletingCase.value = null
  }
}

/* ── 케이스 생성/편집 모달 ── */
const modalOpen = ref(false)
const modalMode = ref<'create' | 'edit'>('create')
const editingCase = ref<TestCase | null>(null)

/** 새 케이스 기본 스위트 — 특정 스위트를 보고 있으면 그 스위트로 */
const defaultSuiteId = computed(() => (typeof selected.value === 'number' ? selected.value : null))

function openCreate() {
  modalMode.value = 'create'
  editingCase.value = null
  modalOpen.value = true
}

function openEdit(c: TestCase) {
  modalMode.value = 'edit'
  editingCase.value = c
  modalOpen.value = true
}

function onSaved(saved: TestCase) {
  const i = cases.value.findIndex((c) => c.id === saved.id)
  if (i >= 0) cases.value[i] = saved
  else cases.value.unshift(saved)
}
</script>

<template>
  <div class="flex overflow-hidden rounded-xl border border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-900">
    <!-- 스위트 사이드바 -->
    <aside class="flex w-56 shrink-0 flex-col border-r border-slate-100 dark:border-slate-800">
      <nav class="flex-1 space-y-0.5 overflow-y-auto p-2">
        <button
          type="button"
          :class="[
            'flex w-full items-center justify-between gap-2 rounded-md px-2.5 py-1.5 text-left text-sm',
            selected === 'all'
              ? 'bg-emerald-50 font-medium text-emerald-700 dark:bg-emerald-500/10 dark:text-emerald-400'
              : 'text-slate-600 hover:bg-slate-50 dark:text-slate-300 dark:hover:bg-slate-800/60',
          ]"
          @click="selected = 'all'"
        >
          <span class="truncate">{{ $t('common.state.all') }}</span>
          <span class="shrink-0 rounded-full bg-slate-100 px-1.5 text-xs text-slate-500 dark:bg-slate-800 dark:text-slate-400">{{ cases.length }}</span>
        </button>
        <button
          type="button"
          :class="[
            'flex w-full items-center justify-between gap-2 rounded-md px-2.5 py-1.5 text-left text-sm',
            selected === 'none'
              ? 'bg-emerald-50 font-medium text-emerald-700 dark:bg-emerald-500/10 dark:text-emerald-400'
              : 'text-slate-600 hover:bg-slate-50 dark:text-slate-300 dark:hover:bg-slate-800/60',
          ]"
          @click="selected = 'none'"
        >
          <span class="truncate">{{ $t('testcase.suite.unsorted') }}</span>
          <span class="shrink-0 rounded-full bg-slate-100 px-1.5 text-xs text-slate-500 dark:bg-slate-800 dark:text-slate-400">{{ unsortedCount }}</span>
        </button>

        <div v-for="s in sortedSuites" :key="s.id">
          <!-- 이름 변경 인라인 입력 -->
          <input
            v-if="renamingId === s.id"
            :ref="focusOnMount"
            v-model="renameName"
            type="text"
            maxlength="100"
            class="w-full rounded-md border border-emerald-300 px-2.5 py-1.5 text-sm focus:outline-none focus:ring-1 focus:ring-emerald-500 dark:border-emerald-500/50 dark:bg-slate-900 dark:text-slate-100"
            @keydown.enter.prevent="submitRename"
            @keydown.esc="renamingId = null"
            @blur="renamingId = null"
          />
          <div
            v-else
            :class="[
              'group flex w-full cursor-pointer items-center gap-2 rounded-md px-2.5 py-1.5 text-sm',
              selected === s.id
                ? 'bg-emerald-50 font-medium text-emerald-700 dark:bg-emerald-500/10 dark:text-emerald-400'
                : 'text-slate-600 hover:bg-slate-50 dark:text-slate-300 dark:hover:bg-slate-800/60',
            ]"
            @click="selected = s.id"
          >
            <span class="min-w-0 flex-1 truncate">{{ s.name }}</span>
            <span class="shrink-0 rounded-full bg-slate-100 px-1.5 text-xs text-slate-500 group-hover:hidden dark:bg-slate-800 dark:text-slate-400">{{ suiteCount(s.id) }}</span>
            <span class="hidden shrink-0 items-center gap-0.5 group-hover:flex">
              <button
                type="button"
                :title="$t('testcase.suite.rename')"
                class="rounded p-0.5 text-slate-400 hover:bg-slate-100 hover:text-emerald-600 dark:text-slate-500 dark:hover:bg-slate-800 dark:hover:text-emerald-400"
                @click.stop="startRename(s)"
              >
                <Pencil class="h-3.5 w-3.5" />
              </button>
              <button
                type="button"
                :title="$t('common.actions.delete')"
                class="rounded p-0.5 text-slate-400 hover:bg-red-50 hover:text-red-600 dark:text-slate-500 dark:hover:bg-red-500/10 dark:hover:text-red-400"
                @click.stop="deletingSuite = s"
              >
                <Trash2 class="h-3.5 w-3.5" />
              </button>
            </span>
          </div>
        </div>
      </nav>

      <!-- 스위트 추가 -->
      <div class="border-t border-slate-100 p-2 dark:border-slate-800">
        <input
          v-if="addingSuite"
          :ref="focusOnMount"
          v-model="newSuiteName"
          type="text"
          maxlength="100"
          :placeholder="$t('testcase.suite.namePlaceholder')"
          class="w-full rounded-md border border-emerald-300 px-2.5 py-1.5 text-sm focus:outline-none focus:ring-1 focus:ring-emerald-500 dark:border-emerald-500/50 dark:bg-slate-900 dark:text-slate-100 dark:placeholder-slate-500"
          @keydown.enter.prevent="submitNewSuite"
          @keydown.esc="addingSuite = false; newSuiteName = ''"
          @blur="addingSuite = false; newSuiteName = ''"
        />
        <button
          v-else
          type="button"
          class="inline-flex w-full items-center gap-1 rounded-md px-2.5 py-1.5 text-sm text-slate-500 hover:bg-slate-50 hover:text-emerald-600 dark:text-slate-400 dark:hover:bg-slate-800/60 dark:hover:text-emerald-400"
          @click="addingSuite = true"
        >
          <Plus class="h-3.5 w-3.5" /> {{ $t('testcase.suite.add') }}
        </button>
      </div>
    </aside>

    <!-- 케이스 테이블 -->
    <div class="flex min-w-0 flex-1 flex-col">
      <div class="flex items-center justify-end border-b border-slate-100 px-4 py-3 dark:border-slate-800">
        <button
          type="button"
          class="inline-flex items-center gap-1 rounded-md bg-emerald-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-emerald-700"
          @click="openCreate"
        >
          <Plus class="h-3.5 w-3.5" /> {{ $t('testcase.list.newCase') }}
        </button>
      </div>

      <div v-if="loading" class="px-5 py-12 text-center text-sm text-slate-400 dark:text-slate-500">
        {{ $t('common.state.loading') }}
      </div>
      <div v-else-if="loadError" class="px-5 py-12 text-center text-sm text-slate-400 dark:text-slate-500">
        <p>{{ $t('testcase.list.loadFailed') }}</p>
        <button
          type="button"
          class="mt-3 rounded-md border border-slate-200 px-3 py-1.5 text-sm text-slate-600 hover:bg-slate-50 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-800/60"
          @click="load"
        >{{ $t('common.actions.retry') }}</button>
      </div>
      <div v-else class="overflow-x-auto">
        <table class="w-full text-left">
          <thead>
            <tr class="border-b border-slate-100 bg-slate-50 dark:border-slate-800 dark:bg-slate-800/50">
              <th class="w-full px-5 py-3.5 text-xs font-medium text-slate-500 dark:text-slate-400">{{ $t('testcase.list.colTitle') }}</th>
              <th class="whitespace-nowrap px-5 py-3.5 text-xs font-medium text-slate-500 dark:text-slate-400">{{ $t('testcase.list.colSteps') }}</th>
              <th class="whitespace-nowrap px-5 py-3.5 text-xs font-medium text-slate-500 dark:text-slate-400">{{ $t('testcase.list.colPriority') }}</th>
              <th class="whitespace-nowrap px-5 py-3.5 text-xs font-medium text-slate-500 dark:text-slate-400 hidden lg:table-cell">{{ $t('testcase.list.colUpdatedAt') }}</th>
              <th class="whitespace-nowrap px-5 py-3.5 text-xs font-medium text-slate-500 dark:text-slate-400">{{ $t('testcase.list.colActions') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="c in filtered"
              :key="c.id"
              class="cursor-pointer border-b border-slate-50 transition hover:bg-slate-50 dark:border-slate-800 dark:hover:bg-slate-800/60"
              @click="openEdit(c)"
            >
              <td class="px-5 py-4">
                <div class="flex flex-wrap items-center gap-1.5">
                  <p class="line-clamp-1 text-sm font-medium text-slate-800 dark:text-slate-100">{{ c.title }}</p>
                  <span
                    v-if="c.origin === 'FLOW'"
                    class="whitespace-nowrap rounded-md bg-violet-50 px-1.5 py-0.5 text-xs font-medium text-violet-600 dark:bg-violet-500/10 dark:text-violet-400"
                  >{{ $t('testcase.list.flowBadge') }}</span>
                  <span
                    v-if="c.flowStale"
                    class="whitespace-nowrap rounded-md bg-amber-50 px-1.5 py-0.5 text-xs font-medium text-amber-600 dark:bg-amber-500/10 dark:text-amber-400"
                  >{{ $t('testcase.list.flowStaleBadge') }}</span>
                </div>
              </td>
              <td class="whitespace-nowrap px-5 py-4 text-sm tabular-nums text-slate-500 dark:text-slate-400">{{ c.steps.length }}</td>
              <td class="whitespace-nowrap px-5 py-4"><PriorityBadge :priority="c.priority" /></td>
              <td class="hidden whitespace-nowrap px-5 py-4 text-xs text-slate-400 lg:table-cell dark:text-slate-500">{{ formatDate(c.updatedAt) }}</td>
              <td class="whitespace-nowrap px-5 py-4">
                <div class="flex items-center gap-1">
                  <button
                    type="button"
                    :title="$t('common.actions.edit')"
                    class="rounded p-1 text-slate-400 hover:bg-slate-100 hover:text-emerald-600 dark:text-slate-500 dark:hover:bg-slate-800 dark:hover:text-emerald-400"
                    @click.stop="openEdit(c)"
                  >
                    <Pencil class="h-4 w-4" />
                  </button>
                  <button
                    type="button"
                    :title="$t('common.actions.delete')"
                    class="rounded p-1 text-slate-400 hover:bg-red-50 hover:text-red-600 dark:text-slate-500 dark:hover:bg-red-500/10 dark:hover:text-red-400"
                    @click.stop="deletingCase = c"
                  >
                    <Trash2 class="h-4 w-4" />
                  </button>
                </div>
              </td>
            </tr>
            <tr v-if="filtered.length === 0">
              <td colspan="5" class="px-5 py-14 text-center">
                <ClipboardList class="mx-auto mb-2 h-7 w-7 text-slate-300 dark:text-slate-600" />
                <p class="text-sm font-medium text-slate-500 dark:text-slate-400">{{ $t('testcase.list.empty') }}</p>
                <p class="mt-1 text-xs text-slate-400 dark:text-slate-500">{{ $t('testcase.list.emptyHint') }}</p>
                <button
                  type="button"
                  class="mt-4 inline-flex items-center gap-1 rounded-md bg-emerald-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-emerald-700"
                  @click="openCreate"
                >
                  <Plus class="h-3.5 w-3.5" /> {{ $t('testcase.list.newCase') }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- 케이스 생성/편집 모달 -->
    <TestCaseEditModal
      :open="modalOpen"
      :mode="modalMode"
      :project-id="projectId"
      :suites="sortedSuites"
      :test-case="editingCase"
      :default-suite-id="defaultSuiteId"
      @close="modalOpen = false"
      @saved="onSaved"
    />

    <!-- 스위트 삭제 확인 -->
    <DeleteConfirmModal
      :open="deletingSuite != null"
      :title="$t('testcase.suite.deleteTitle')"
      :message="$t('testcase.suite.deleteMessage')"
      @confirm="confirmDeleteSuite"
      @cancel="deletingSuite = null"
    />

    <!-- 케이스 삭제 확인 -->
    <DeleteConfirmModal
      :open="deletingCase != null"
      @confirm="confirmDeleteCase"
      @cancel="deletingCase = null"
    />
  </div>
</template>
