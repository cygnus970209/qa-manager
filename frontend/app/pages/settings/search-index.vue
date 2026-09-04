<script setup lang="ts">
import { AlertTriangle, CheckCircle2, DatabaseZap, Loader2, RefreshCw, Stethoscope, Wrench } from '@lucide/vue'
import SettingsHeader from '~/components/base/SettingsHeader.vue'
import type { SearchCheck, SearchStatus, SearchType } from '~/types/api'

/**
 * 검색 인덱스 관리 (관리자).
 * - 현황: 종류별 원본/색인 건수, 마지막 재생성 정보 — 건수만 달라도 바로 눈에 띈다
 * - 상태 검사: 원본과 문서를 하나씩 대조해 누락·고아·내용 변경을 찾는다 (읽기 전용)
 * - 불일치 복구: 검사에서 나온 것만 고친다 / 전체 다시 만들기: 처음부터 다시
 */
definePageMeta({ layout: 'settings' })

// 관리자 전용 — 일반 멤버는 내 계정으로 돌려보낸다 (API 는 백엔드 403 으로 별도 보호됨)
const auth = useAuthStore()
const router = useRouter()
watchEffect(() => {
  if (auth.user && auth.user.accountRole !== 'ADMIN') router.replace('/settings/account')
})

const searchApi = useSearch()
const { t } = useI18n()
const TYPES: SearchType[] = ['qa', 'comment', 'project', 'update', 'test_case']

const status = ref<SearchStatus | null>(null)
const check = ref<SearchCheck | null>(null)
const loading = ref(true)
const busy = ref<'check' | 'repair' | 'reindex' | null>(null)
const message = ref('')
const isError = ref(false)

async function loadStatus() {
  loading.value = true
  try {
    status.value = await searchApi.status()
  } catch {
    status.value = null
  } finally {
    loading.value = false
  }
}
onMounted(loadStatus)

async function run(kind: 'check' | 'repair' | 'reindex') {
  if (busy.value) return
  busy.value = kind
  message.value = ''
  isError.value = false
  try {
    if (kind === 'check') {
      check.value = await searchApi.check()
      message.value = check.value.ok ? t('shell.settings.searchIndex.checkOk') : t('shell.settings.searchIndex.checkIssues', { n: check.value.issues.toLocaleString() })
      isError.value = !check.value.ok
    } else if (kind === 'repair') {
      check.value = await searchApi.repair()
      message.value = check.value.ok ? t('shell.settings.searchIndex.repairDone') : t('shell.settings.searchIndex.checkIssues', { n: check.value.issues.toLocaleString() })
      isError.value = !check.value.ok
      await loadStatus()
    } else {
      status.value = await searchApi.reindex()
      check.value = null
      message.value = t('shell.settings.searchIndex.done', { n: status.value.total.toLocaleString() })
    }
  } catch (e: any) {
    isError.value = true
    message.value = e?.data?.message ?? t('shell.settings.searchIndex.failed')
  } finally {
    busy.value = null
  }
}

/** 검사 전에도 원본/색인 건수 차이로 대략의 상태를 보여준다 */
function countDiff(tp: SearchType) {
  if (!status.value) return 0
  return (status.value.source[tp] ?? 0) - (status.value.indexed[tp] ?? 0)
}
const anyDiff = computed(() => TYPES.some((tp) => countDiff(tp) !== 0))

function triggerLabel(tr: string | null) {
  if (!tr) return ''
  return t(`shell.settings.searchIndex.trigger.${tr}`)
}
function fmt(ts: string | null | undefined) {
  return ts ? ts.slice(0, 19).replace('T', ' ') : '-'
}

const btnCls = 'inline-flex items-center gap-1.5 whitespace-nowrap rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-xs font-medium text-slate-700 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-200 dark:hover:bg-slate-800/60'
const primaryCls = 'inline-flex items-center gap-1.5 whitespace-nowrap rounded-lg bg-emerald-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-emerald-700 disabled:cursor-not-allowed disabled:opacity-60'
</script>

<template>
  <div>
    <SettingsHeader :title="$t('shell.settings.searchIndex.title')" :subtitle="$t('shell.settings.searchIndex.subtitle')" />

    <div class="max-w-3xl space-y-5">
      <!-- 현황 -->
      <div class="rounded-xl border border-slate-200 bg-white p-5 dark:border-slate-800 dark:bg-slate-900">
        <div class="flex flex-wrap items-start justify-between gap-4">
          <div class="flex items-start gap-3">
            <div class="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-slate-100 text-slate-500 dark:bg-slate-800 dark:text-slate-400">
              <DatabaseZap class="h-5 w-5" />
            </div>
            <div>
              <p class="text-sm font-semibold text-slate-800 dark:text-slate-100">{{ $t('shell.settings.searchIndex.docs') }}</p>
              <p class="mt-1 text-2xl font-bold text-slate-800 dark:text-slate-100">
                <template v-if="status">{{ status.total.toLocaleString() }}</template>
                <template v-else-if="loading">…</template>
                <template v-else>-</template>
              </p>
              <p class="mt-1 text-xs text-slate-400 dark:text-slate-500">
                <template v-if="status?.lastReindexAt">
                  {{ $t('shell.settings.searchIndex.lastReindex', { at: fmt(status.lastReindexAt), ms: status.lastReindexMs ?? 0, trigger: triggerLabel(status.lastTrigger) }) }}
                </template>
                <template v-else-if="status">{{ $t('shell.settings.searchIndex.noReindexInfo') }}</template>
                <template v-else-if="!loading">{{ $t('shell.settings.searchIndex.unavailable') }}</template>
              </p>
            </div>
          </div>
          <div class="flex flex-wrap items-center gap-2">
            <span v-if="status?.running" class="inline-flex items-center gap-1 rounded-full bg-amber-50 px-2.5 py-0.5 text-xs font-medium text-amber-600 dark:bg-amber-500/10 dark:text-amber-400">
              <Loader2 class="h-3 w-3 animate-spin" /> {{ $t('shell.settings.searchIndex.running') }}
            </span>
            <button type="button" :class="primaryCls" :disabled="busy !== null || !status" @click="run('check')">
              <Loader2 v-if="busy === 'check'" class="h-3.5 w-3.5 animate-spin" />
              <Stethoscope v-else class="h-3.5 w-3.5" />
              {{ busy === 'check' ? $t('shell.settings.searchIndex.checking') : $t('shell.settings.searchIndex.check') }}
            </button>
            <button type="button" :class="btnCls" :disabled="busy !== null" @click="run('reindex')">
              <Loader2 v-if="busy === 'reindex'" class="h-3.5 w-3.5 animate-spin" />
              <RefreshCw v-else class="h-3.5 w-3.5" />
              {{ busy === 'reindex' ? $t('shell.settings.searchIndex.reindexing') : $t('shell.settings.searchIndex.reindex') }}
            </button>
          </div>
        </div>
      </div>

      <!-- 종류별 표 -->
      <div class="overflow-hidden rounded-xl border border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-900">
        <table class="w-full text-left text-sm">
          <thead class="border-b border-slate-100 bg-slate-50 dark:border-slate-800 dark:bg-slate-800/50">
            <tr>
              <th class="px-4 py-3 text-xs font-medium text-slate-500 dark:text-slate-400">{{ $t('shell.settings.searchIndex.colType') }}</th>
              <th class="w-24 px-4 py-3 text-right text-xs font-medium text-slate-500 dark:text-slate-400">{{ $t('shell.settings.searchIndex.colSource') }}</th>
              <th class="w-24 px-4 py-3 text-right text-xs font-medium text-slate-500 dark:text-slate-400">{{ $t('shell.settings.searchIndex.colIndexed') }}</th>
              <th class="w-64 px-4 py-3 text-xs font-medium text-slate-500 dark:text-slate-400">{{ $t('shell.settings.searchIndex.colState') }}</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-100 dark:divide-slate-800">
            <tr v-for="tp in TYPES" :key="tp">
              <td class="px-4 py-3 text-slate-700 dark:text-slate-200">{{ $t(`shell.search.types.${tp}`) }}</td>
              <td class="px-4 py-3 text-right tabular-nums text-slate-600 dark:text-slate-300">{{ status ? (status.source[tp] ?? 0).toLocaleString() : '-' }}</td>
              <td class="px-4 py-3 text-right tabular-nums text-slate-600 dark:text-slate-300">{{ status ? (status.indexed[tp] ?? 0).toLocaleString() : '-' }}</td>
              <td class="px-4 py-3">
                <template v-if="check">
                  <span v-if="check.byType[tp].missing + check.byType[tp].orphan + check.byType[tp].stale === 0" class="inline-flex items-center gap-1 text-xs text-emerald-600 dark:text-emerald-400">
                    <CheckCircle2 class="h-3.5 w-3.5" /> {{ $t('shell.settings.searchIndex.stateOk') }}
                  </span>
                  <span v-else class="inline-flex flex-wrap items-center gap-1.5 text-xs text-rose-600 dark:text-rose-400">
                    <AlertTriangle class="h-3.5 w-3.5" />
                    <span v-if="check.byType[tp].missing">{{ $t('shell.settings.searchIndex.missing', { n: check.byType[tp].missing }) }}</span>
                    <span v-if="check.byType[tp].orphan">{{ $t('shell.settings.searchIndex.orphan', { n: check.byType[tp].orphan }) }}</span>
                    <span v-if="check.byType[tp].stale">{{ $t('shell.settings.searchIndex.stale', { n: check.byType[tp].stale }) }}</span>
                  </span>
                  <p v-if="check.byType[tp].sampleMissing.length + check.byType[tp].sampleOrphan.length + check.byType[tp].sampleStale.length > 0" class="mt-0.5 text-[11px] text-slate-400 dark:text-slate-500">
                    id: {{ [...check.byType[tp].sampleMissing, ...check.byType[tp].sampleStale, ...check.byType[tp].sampleOrphan].slice(0, 10).join(', ') }}
                  </p>
                </template>
                <template v-else-if="status">
                  <span v-if="countDiff(tp) === 0" class="text-xs text-slate-400 dark:text-slate-500">{{ $t('shell.settings.searchIndex.stateCountOk') }}</span>
                  <span v-else class="inline-flex items-center gap-1 text-xs text-amber-600 dark:text-amber-400">
                    <AlertTriangle class="h-3.5 w-3.5" /> {{ $t('shell.settings.searchIndex.stateCountDiff', { n: countDiff(tp) > 0 ? `+${countDiff(tp)}` : countDiff(tp) }) }}
                  </span>
                </template>
                <span v-else class="text-xs text-slate-400 dark:text-slate-500">-</span>
              </td>
            </tr>
          </tbody>
        </table>
        <div class="flex flex-wrap items-center justify-between gap-3 border-t border-slate-100 px-4 py-3 dark:border-slate-800">
          <p class="text-xs text-slate-400 dark:text-slate-500">
            <template v-if="check">{{ $t('shell.settings.searchIndex.checkedAt', { at: fmt(check.checkedAt) }) }}</template>
            <template v-else-if="anyDiff">{{ $t('shell.settings.searchIndex.countDiffHint') }}</template>
            <template v-else>{{ $t('shell.settings.searchIndex.checkHint') }}</template>
          </p>
          <button v-if="check && !check.ok" type="button" :class="primaryCls" :disabled="busy !== null" @click="run('repair')">
            <Loader2 v-if="busy === 'repair'" class="h-3.5 w-3.5 animate-spin" />
            <Wrench v-else class="h-3.5 w-3.5" />
            {{ busy === 'repair' ? $t('shell.settings.searchIndex.repairing') : $t('shell.settings.searchIndex.repair', { n: check.issues.toLocaleString() }) }}
          </button>
        </div>
      </div>

      <p v-if="message" :class="['text-sm', isError ? 'text-rose-600 dark:text-rose-400' : 'text-emerald-600 dark:text-emerald-400']">{{ message }}</p>
      <p class="text-xs text-slate-400 dark:text-slate-500">{{ $t('shell.settings.searchIndex.hint') }}</p>
    </div>
  </div>
</template>
