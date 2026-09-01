<script setup lang="ts">
import { ArrowLeft, Bug, Circle, CircleCheck, CircleMinus, CircleX, ExternalLink, Lock, LockOpen, OctagonAlert, RotateCcw } from '@lucide/vue'
import ImageLightbox from '~/components/base/ImageLightbox.vue'
import PriorityBadge from '~/components/base/PriorityBadge.vue'
import NewQAModal from '~/components/feature/NewQAModal.vue'
import type {
  Member,
  Project,
  ProjectUpdate,
  QaItem,
  TestRunCase,
  TestRunCaseResult,
  TestRunDetail,
} from '~/types/api'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()
const runId = computed(() => Number(route.params.id))

const testingApi = useTesting()
const updatesApi = useUpdates()
const projectsApi = useProjects()
const membersApi = useMembers()

const detail = ref<TestRunDetail | null>(null)
const update = ref<ProjectUpdate | null>(null)
const loading = ref(true)
const error = ref<string | null>(null)
/** 결과/메모 저장 등 개별 액션 실패 메시지 */
const actionError = ref<string | null>(null)

const cases = computed(() =>
  detail.value ? [...detail.value.cases].sort((a, b) => a.sortOrder - b.sortOrder) : [],
)
const selectedId = ref<number | null>(null)
const selectedCase = computed(() => cases.value.find((c) => c.id === selectedId.value) ?? null)

/** 스텝 참고 이미지 확대 보기 */
const stepLightboxSrc = ref<string | null>(null)

/* ─── 플랫폼 (실행 항목별 PC/Android/iOS, null=공통) ─── */
const PLATFORM_LABELS: Record<string, string> = { PC: 'PC', ANDROID: 'Android', IOS: 'iOS' }
const platformsInRun = computed(() => {
  const out: string[] = []
  for (const c of cases.value) {
    if (c.platform && !out.includes(c.platform)) out.push(c.platform)
  }
  return out
})
const platformFilter = ref<string>('all')
const visibleCases = computed(() =>
  platformFilter.value === 'all' ? cases.value : cases.value.filter((c) => c.platform === platformFilter.value),
)

const isClosed = computed(() => !!detail.value?.run.closedAt)

/** 로컬 반영된 케이스 기준으로 재계산한 통계 (run.stats 는 로드 시점 값) */
const stats = computed(() => {
  const list = cases.value
  const count = (r: TestRunCaseResult) => list.filter((c) => c.result === r).length
  const pending = count('PENDING')
  return {
    total: list.length,
    executed: list.length - pending,
    pass: count('PASS'),
    fail: count('FAIL'),
    blocked: count('BLOCKED'),
    skip: count('SKIP'),
  }
})
const percent = computed(() =>
  stats.value.total > 0 ? Math.round((stats.value.executed / stats.value.total) * 100) : 0,
)

async function load() {
  loading.value = true
  error.value = null
  actionError.value = null
  try {
    const d = await testingApi.getRun(runId.value)
    detail.value = d
    const sorted = [...d.cases].sort((a, b) => a.sortOrder - b.sortOrder)
    selectedId.value = sorted[0]?.id ?? null
    // 업데이트 정보(버전·제목/뒤로가기 경로)는 실패해도 페이지는 동작한다
    try {
      update.value = await updatesApi.get(d.run.updateId)
    } catch {
      update.value = null
    }
  } catch (e: any) {
    error.value = e?.data?.message ?? t('testrun.detail.loadFailed')
  } finally {
    loading.value = false
  }
}
if (import.meta.client) onMounted(load)
watch(runId, () => { if (import.meta.client) load() })

function goBack() {
  if (update.value) router.push(`/project/${update.value.projectId}`)
  else router.back()
}

/* ─── 결과 아이콘/버튼 메타 ─── */
const RESULT_META: Record<TestRunCaseResult, { icon: any; cls: string }> = {
  PASS:    { icon: CircleCheck, cls: 'text-emerald-500 dark:text-emerald-400' },
  FAIL:    { icon: CircleX, cls: 'text-rose-500 dark:text-rose-400' },
  BLOCKED: { icon: OctagonAlert, cls: 'text-amber-500 dark:text-amber-400' },
  SKIP:    { icon: CircleMinus, cls: 'text-slate-400 dark:text-slate-500' },
  PENDING: { icon: Circle, cls: 'text-slate-300 dark:text-slate-600' },
}
const RESULT_BUTTONS: { key: Exclude<TestRunCaseResult, 'PENDING'>; active: string; idle: string }[] = [
  { key: 'PASS',    active: 'bg-emerald-600 text-white', idle: 'bg-emerald-50 text-emerald-600 hover:bg-emerald-100 dark:bg-emerald-500/10 dark:text-emerald-400 dark:hover:bg-emerald-500/20' },
  { key: 'FAIL',    active: 'bg-rose-600 text-white',    idle: 'bg-rose-50 text-rose-600 hover:bg-rose-100 dark:bg-rose-500/10 dark:text-rose-400 dark:hover:bg-rose-500/20' },
  { key: 'BLOCKED', active: 'bg-amber-500 text-white',   idle: 'bg-amber-50 text-amber-600 hover:bg-amber-100 dark:bg-amber-500/10 dark:text-amber-400 dark:hover:bg-amber-500/20' },
  { key: 'SKIP',    active: 'bg-slate-600 text-white',   idle: 'bg-slate-100 text-slate-600 hover:bg-slate-200 dark:bg-slate-800 dark:text-slate-300 dark:hover:bg-slate-700' },
]

function applyCase(next: TestRunCase) {
  if (!detail.value) return
  detail.value = {
    ...detail.value,
    cases: detail.value.cases.map((x) => (x.id === next.id ? next : x)),
  }
}

/* ─── 결과 저장 (클릭 즉시) ─── */
const savingResult = ref(false)
async function setResult(c: TestRunCase, result: TestRunCaseResult) {
  if (isClosed.value || savingResult.value || c.result === result) return
  savingResult.value = true
  actionError.value = null
  try {
    applyCase(await testingApi.updateRunCase(c.id, { result }))
  } catch (e: any) {
    actionError.value = e?.data?.message ?? t('testrun.detail.saveFailed')
  } finally {
    savingResult.value = false
  }
}

/* ─── 메모 (blur 시 저장) ─── */
const noteDraft = ref('')
watch(selectedCase, (c, prev) => {
  if (c?.id !== prev?.id) noteDraft.value = c?.note ?? ''
})
async function saveNote() {
  const c = selectedCase.value
  if (!c || isClosed.value) return
  if (noteDraft.value === (c.note ?? '')) return
  actionError.value = null
  try {
    applyCase(await testingApi.updateRunCase(c.id, { note: noteDraft.value }))
  } catch (e: any) {
    actionError.value = e?.data?.message ?? t('testrun.detail.saveFailed')
  }
}

/* ─── 런 종료/다시 열기 ─── */
const closing = ref(false)
async function toggleClosed() {
  if (!detail.value || closing.value) return
  closing.value = true
  actionError.value = null
  try {
    const run = await testingApi.updateRun(detail.value.run.id, { closed: !isClosed.value })
    detail.value = { ...detail.value, run }
  } catch (e: any) {
    actionError.value = e?.data?.message ?? t('testrun.detail.saveFailed')
  } finally {
    closing.value = false
  }
}

/* ─── FAIL 케이스 → QA 항목 생성 ─── */
const qaOpen = ref(false)
const qaOpening = ref(false)
const qaModalReady = ref(false)
const qaTarget = ref<TestRunCase | null>(null)
const qaProjects = ref<Project[]>([])
const qaUpdates = ref<ProjectUpdate[]>([])
const qaMembers = ref<Member[]>([])

async function openQaModal(c: TestRunCase) {
  if (qaOpening.value) return
  qaTarget.value = c
  qaOpening.value = true
  actionError.value = null
  try {
    // 모달 보조 데이터는 최초 1회만 로드
    if (!qaModalReady.value) {
      const pid = update.value?.projectId
      const [ps, ms, us] = await Promise.all([
        projectsApi.list(),
        membersApi.list(),
        pid != null ? updatesApi.listByProject(pid) : updatesApi.listAll(),
      ])
      qaProjects.value = ps
      qaMembers.value = ms
      qaUpdates.value = us
      qaModalReady.value = true
    }
    qaOpen.value = true
  } catch (e: any) {
    actionError.value = e?.data?.message ?? t('testrun.detail.saveFailed')
  } finally {
    qaOpening.value = false
  }
}

const qaDefaultTitle = computed(() => {
  if (!qaTarget.value) return ''
  const platform = qaTarget.value.platform ? `·${PLATFORM_LABELS[qaTarget.value.platform] ?? qaTarget.value.platform}` : ''
  return `[TC${platform}] ${qaTarget.value.title}`
})
const qaDefaultDescription = computed(() => {
  const c = qaTarget.value
  if (!c) return ''
  const lines = c.steps.map((s, i) => `${i + 1}. ${s.action} — ${s.expected}`)
  if (c.note) lines.push('', `${t('testrun.qa.noteLabel')}: ${c.note}`)
  return lines.join('\n')
})

async function onQaCreated(qa: QaItem) {
  const c = qaTarget.value
  if (!c) return
  actionError.value = null
  try {
    applyCase(await testingApi.updateRunCase(c.id, { qaItemId: qa.id }))
  } catch (e: any) {
    actionError.value = e?.data?.message ?? t('testrun.detail.saveFailed')
  }
}

/* ─── 키보드 ↑/↓ 케이스 이동 (입력 포커스 중엔 무시) ─── */
function onKeydown(e: KeyboardEvent) {
  if (e.key !== 'ArrowUp' && e.key !== 'ArrowDown') return
  const el = e.target as HTMLElement | null
  if (el && (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA' || el.tagName === 'SELECT' || el.isContentEditable)) return
  const list = visibleCases.value
  if (list.length === 0) return
  const idx = list.findIndex((c) => c.id === selectedId.value)
  const next = idx < 0
    ? 0
    : e.key === 'ArrowDown'
      ? Math.min(idx + 1, list.length - 1)
      : Math.max(idx - 1, 0)
  selectedId.value = list[next]!.id
  e.preventDefault()
}
onMounted(() => window.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))
</script>

<template>
  <section>
    <!-- 로딩 스켈레톤 -->
    <div v-if="loading" class="animate-pulse space-y-4">
      <div class="h-4 w-24 rounded bg-slate-200 dark:bg-slate-800" />
      <div class="h-7 w-64 rounded bg-slate-200 dark:bg-slate-800" />
      <div class="flex flex-col gap-4 lg:flex-row">
        <div class="h-96 w-full rounded-xl border border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-900 lg:w-80" />
        <div class="h-96 flex-1 rounded-xl border border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-900" />
      </div>
    </div>

    <div v-else-if="error" class="rounded-md bg-red-50 px-4 py-3 text-sm text-red-700 dark:bg-red-500/10 dark:text-red-400">{{ error }}</div>

    <template v-else-if="detail">
      <!-- 헤더 -->
      <div class="mb-4">
        <button
          type="button"
          class="inline-flex items-center gap-1 text-xs text-slate-500 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
          @click="goBack"
        >
          <ArrowLeft class="h-3.5 w-3.5" /> {{ $t('testrun.detail.back') }}
        </button>
        <div class="mt-2 flex flex-wrap items-center gap-3">
          <div class="min-w-0">
            <div class="flex min-w-0 items-center gap-2">
              <h1 class="truncate text-lg font-bold text-slate-800 dark:text-slate-100">{{ detail.run.name }}</h1>
              <span
                v-if="isClosed"
                class="shrink-0 rounded-full bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-500 dark:bg-slate-800 dark:text-slate-400"
              >{{ $t('testrun.detail.closedBadge') }}</span>
            </div>
            <p v-if="update" class="mt-0.5 truncate text-xs text-slate-400 dark:text-slate-500">
              {{ $t('testrun.detail.updateLabel', { version: update.version, title: update.title }) }}
            </p>
          </div>
          <div class="ml-auto flex flex-wrap items-center gap-2 text-xs">
            <span class="text-slate-500 dark:text-slate-400">{{ $t('testrun.detail.statTotal') }} <b class="tabular-nums">{{ stats.total }}</b></span>
            <span class="text-slate-500 dark:text-slate-400">{{ $t('testrun.detail.statExecuted') }} <b class="tabular-nums">{{ stats.executed }}</b></span>
            <span class="rounded-full bg-emerald-50 px-2 py-0.5 font-medium text-emerald-600 dark:bg-emerald-500/10 dark:text-emerald-400">{{ $t('testrun.result.pass') }} {{ stats.pass }}</span>
            <span class="rounded-full bg-rose-50 px-2 py-0.5 font-medium text-rose-600 dark:bg-rose-500/10 dark:text-rose-400">{{ $t('testrun.result.fail') }} {{ stats.fail }}</span>
            <button
              type="button"
              :disabled="closing"
              class="inline-flex items-center gap-1 rounded-md border border-slate-200 px-3 py-1.5 font-medium text-slate-600 hover:bg-slate-50 disabled:opacity-60 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-800/60"
              @click="toggleClosed"
            >
              <component :is="isClosed ? LockOpen : Lock" class="h-3.5 w-3.5" />
              {{ isClosed ? $t('testrun.detail.reopenRun') : $t('testrun.detail.closeRun') }}
            </button>
          </div>
        </div>
      </div>

      <p v-if="actionError" class="mb-3 rounded bg-red-50 px-3 py-2 text-xs text-red-700 dark:bg-red-500/10 dark:text-red-400">{{ actionError }}</p>

      <div class="flex flex-col gap-4 lg:flex-row">
        <!-- 좌측: 케이스 목록 -->
        <aside class="w-full shrink-0 lg:w-80">
          <div class="rounded-xl border border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-900">
            <div class="border-b border-slate-100 px-4 py-3 dark:border-slate-800">
              <div class="flex items-center justify-between text-xs">
                <span class="font-semibold text-slate-600 dark:text-slate-300">{{ $t('testrun.detail.caseListTitle') }}</span>
                <span class="tabular-nums text-slate-400 dark:text-slate-500">{{ stats.executed }}/{{ stats.total }}</span>
              </div>
              <div class="mt-2 h-1.5 overflow-hidden rounded-full bg-slate-100 dark:bg-slate-800">
                <div class="h-full rounded-full bg-emerald-500 transition-all dark:bg-emerald-400" :style="{ width: percent + '%' }" />
              </div>
              <!-- 플랫폼 필터 (플랫폼 지정 런에서만 노출) -->
              <div v-if="platformsInRun.length > 0" class="mt-2 flex flex-wrap items-center gap-1">
                <button
                  type="button"
                  :class="[
                    'rounded-full px-2 py-0.5 text-[11px] font-medium transition-colors',
                    platformFilter === 'all'
                      ? 'bg-slate-800 text-white dark:bg-slate-200 dark:text-slate-900'
                      : 'bg-slate-100 text-slate-500 hover:bg-slate-200 dark:bg-slate-800 dark:text-slate-400 dark:hover:bg-slate-700',
                  ]"
                  @click="platformFilter = 'all'"
                >{{ $t('common.state.all') }}</button>
                <button
                  v-for="p in platformsInRun"
                  :key="p"
                  type="button"
                  :class="[
                    'rounded-full px-2 py-0.5 text-[11px] font-medium transition-colors',
                    platformFilter === p
                      ? 'bg-slate-800 text-white dark:bg-slate-200 dark:text-slate-900'
                      : 'bg-slate-100 text-slate-500 hover:bg-slate-200 dark:bg-slate-800 dark:text-slate-400 dark:hover:bg-slate-700',
                  ]"
                  @click="platformFilter = p"
                >{{ PLATFORM_LABELS[p] ?? p }}</button>
              </div>
            </div>
            <ul v-if="visibleCases.length > 0" class="max-h-[calc(100vh-16rem)] divide-y divide-slate-100 overflow-y-auto dark:divide-slate-800">
              <li v-for="c in visibleCases" :key="c.id">
                <button
                  type="button"
                  :class="[
                    'flex w-full items-center gap-2 px-4 py-2.5 text-left transition',
                    c.id === selectedId ? 'bg-emerald-50/60 dark:bg-emerald-500/10' : 'hover:bg-slate-50 dark:hover:bg-slate-800/60',
                  ]"
                  @click="selectedId = c.id"
                >
                  <component :is="RESULT_META[c.result].icon" :class="['h-4 w-4 shrink-0', RESULT_META[c.result].cls]" />
                  <span
                    :class="[
                      'min-w-0 flex-1 truncate text-sm',
                      c.id === selectedId ? 'font-medium text-slate-800 dark:text-slate-100' : 'text-slate-600 dark:text-slate-300',
                    ]"
                  >{{ c.title }}</span>
                  <span
                    v-if="c.platform"
                    class="shrink-0 rounded bg-slate-100 px-1.5 py-0.5 text-[10px] font-medium text-slate-500 dark:bg-slate-800 dark:text-slate-400"
                  >{{ PLATFORM_LABELS[c.platform] ?? c.platform }}</span>
                </button>
              </li>
            </ul>
            <div v-else class="px-4 py-8 text-center text-xs text-slate-400 dark:text-slate-500">{{ $t('testrun.detail.emptyCases') }}</div>
          </div>
        </aside>

        <!-- 중앙: 선택 케이스 카드 -->
        <div class="min-w-0 flex-1">
          <div v-if="selectedCase" class="rounded-xl border border-slate-200 bg-white p-5 dark:border-slate-800 dark:bg-slate-900">
            <div class="flex flex-wrap items-center gap-2">
              <h2 class="min-w-0 flex-1 text-base font-semibold text-slate-800 dark:text-slate-100">{{ selectedCase.title }}</h2>
              <span
                v-if="selectedCase.platform"
                class="rounded-md bg-sky-50 px-2 py-0.5 text-xs font-medium text-sky-600 dark:bg-sky-500/10 dark:text-sky-400"
              >{{ PLATFORM_LABELS[selectedCase.platform] ?? selectedCase.platform }}</span>
              <PriorityBadge :priority="selectedCase.priority" />
              <NuxtLink
                v-if="selectedCase.qaItemId"
                :to="`/qa/${selectedCase.qaItemId}`"
                class="inline-flex shrink-0 items-center gap-1 rounded-md bg-blue-50 px-2 py-1 text-xs font-medium text-blue-600 hover:bg-blue-100 dark:bg-blue-500/10 dark:text-blue-400 dark:hover:bg-blue-500/20"
              >
                <ExternalLink class="h-3 w-3" /> {{ $t('testrun.detail.viewQa', { id: selectedCase.qaItemId }) }}
              </NuxtLink>
            </div>

            <!-- 종료된 런 안내 -->
            <div v-if="isClosed" class="mt-3 flex items-center gap-1.5 rounded-md bg-amber-50 px-3 py-2 text-xs text-amber-600 dark:bg-amber-500/10 dark:text-amber-400">
              <Lock class="h-3.5 w-3.5 shrink-0" /> {{ $t('testrun.detail.closedNotice') }}
            </div>

            <!-- 사전 조건 -->
            <div v-if="selectedCase.precondition" class="mt-4">
              <span class="text-xs font-medium text-slate-500 dark:text-slate-400">{{ $t('testrun.detail.precondition') }}</span>
              <p class="mt-1 whitespace-pre-wrap rounded-md bg-slate-50 px-3 py-2 text-sm text-slate-700 dark:bg-slate-800/50 dark:text-slate-200">{{ selectedCase.precondition }}</p>
            </div>

            <!-- 스텝 테이블 -->
            <div class="mt-4">
              <span class="text-xs font-medium text-slate-500 dark:text-slate-400">{{ $t('testrun.detail.steps') }}</span>
              <div class="mt-1 overflow-x-auto rounded-lg border border-slate-200 dark:border-slate-800">
                <table class="w-full text-sm">
                  <thead>
                    <tr class="bg-slate-50 text-left text-xs text-slate-500 dark:bg-slate-800/50 dark:text-slate-400">
                      <th class="w-10 px-3 py-2 font-medium">{{ $t('testrun.detail.stepNo') }}</th>
                      <th class="px-3 py-2 font-medium">{{ $t('testrun.detail.stepAction') }}</th>
                      <th class="px-3 py-2 font-medium">{{ $t('testrun.detail.stepExpected') }}</th>
                    </tr>
                  </thead>
                  <tbody class="divide-y divide-slate-100 dark:divide-slate-800">
                    <tr v-for="(s, i) in selectedCase.steps" :key="i" class="align-top">
                      <td class="px-3 py-2 tabular-nums text-slate-400 dark:text-slate-500">{{ i + 1 }}</td>
                      <td class="whitespace-pre-wrap px-3 py-2 text-slate-700 dark:text-slate-200">
                        {{ s.action }}
                        <!-- 스텝 참고 이미지 (플로우 노드에서 전달) — 클릭 시 확대 -->
                        <img
                          v-if="s.image"
                          :src="s.image"
                          :alt="s.action"
                          class="mt-1.5 h-20 max-w-[240px] cursor-zoom-in rounded-md border border-slate-200 object-cover dark:border-slate-700"
                          @click="stepLightboxSrc = s.image"
                        />
                      </td>
                      <td class="whitespace-pre-wrap px-3 py-2 text-slate-600 dark:text-slate-300">{{ s.expected }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>

            <!-- 결과 버튼 -->
            <div class="mt-5 flex flex-wrap items-center gap-2">
              <button
                v-for="b in RESULT_BUTTONS"
                :key="b.key"
                type="button"
                :disabled="isClosed || savingResult"
                :class="[
                  'rounded-md px-3 py-1.5 text-sm font-medium transition disabled:cursor-not-allowed disabled:opacity-50',
                  selectedCase.result === b.key ? b.active : b.idle,
                ]"
                @click="setResult(selectedCase, b.key)"
              >
                {{ $t('testrun.result.' + b.key.toLowerCase()) }}
              </button>
              <button
                v-if="selectedCase.result !== 'PENDING'"
                type="button"
                :disabled="isClosed || savingResult"
                class="inline-flex items-center gap-1 text-xs text-slate-400 hover:text-slate-600 disabled:cursor-not-allowed disabled:opacity-50 dark:text-slate-500 dark:hover:text-slate-300"
                @click="setResult(selectedCase, 'PENDING')"
              >
                <RotateCcw class="h-3 w-3" /> {{ $t('testrun.detail.resetToPending') }}
              </button>
              <button
                v-if="selectedCase.result === 'FAIL' && !selectedCase.qaItemId"
                type="button"
                :disabled="qaOpening || isClosed"
                class="ml-auto inline-flex items-center gap-1 rounded-md bg-blue-500 px-2.5 py-1.5 text-xs font-medium text-white hover:bg-blue-600 disabled:opacity-60"
                @click="openQaModal(selectedCase)"
              >
                <Bug class="h-3.5 w-3.5" /> {{ $t('testrun.detail.createQa') }}
              </button>
            </div>

            <!-- 메모 -->
            <label class="mt-5 block">
              <span class="text-xs font-medium text-slate-500 dark:text-slate-400">{{ $t('testrun.detail.note') }}</span>
              <textarea
                v-model="noteDraft"
                rows="3"
                maxlength="4000"
                :disabled="isClosed"
                :placeholder="$t('testrun.detail.notePlaceholder')"
                class="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500 disabled:bg-slate-100 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100 dark:placeholder-slate-500 dark:disabled:bg-slate-800"
                @blur="saveNote"
              />
            </label>
          </div>
          <div v-else class="rounded-xl border border-dashed border-slate-200 px-4 py-16 text-center text-sm text-slate-400 dark:border-slate-800 dark:text-slate-500">
            {{ $t('testrun.detail.selectCase') }}
          </div>
        </div>
      </div>

      <NewQAModal
        :open="qaOpen"
        :projects="qaProjects"
        :updates="qaUpdates"
        :members="qaMembers"
        :default-update-id="detail.run.updateId"
        :default-title="qaDefaultTitle"
        :default-description="qaDefaultDescription"
        @close="qaOpen = false"
        @created="onQaCreated"
      />

      <!-- 스텝 참고 이미지 확대 보기 -->
      <ImageLightbox :src="stepLightboxSrc" @close="stepLightboxSrc = null" />
    </template>
  </section>
</template>
