<script setup lang="ts">
import { Bug, ClipboardList, Folder, Inbox, MessageSquare, Search, Tag } from '@lucide/vue'
import StatusBadge from '~/components/base/StatusBadge.vue'
import AppSelect from '~/components/base/AppSelect.vue'
import type { SelectOption } from '~/composables/useSelectOptions'
import { highlight, searchItemPath } from '~/composables/useSearch'
import type { Project, SearchItem, SearchResponse, SearchType } from '~/types/api'

/**
 * 통합 검색 페이지. 질의어는 URL(?q=) 에 두어 새로고침·공유가 되게 하고, 입력은 250ms 디바운스로 반영한다.
 * 종류 탭(전체/QA/코멘트/프로젝트/업데이트/테스트 케이스) + 프로젝트 필터 + 페이지네이션.
 */
const route = useRoute()
const router = useRouter()
const searchApi = useSearch()
const projectsApi = useProjects()
const { t } = useI18n()

const PAGE_SIZE = 20
const TYPES: { key: SearchType; label: string; icon: unknown }[] = [
  { key: 'qa', label: 'shell.search.types.qa', icon: Bug },
  { key: 'comment', label: 'shell.search.types.comment', icon: MessageSquare },
  { key: 'project', label: 'shell.search.types.project', icon: Folder },
  { key: 'update', label: 'shell.search.types.update', icon: Tag },
  { key: 'test_case', label: 'shell.search.types.test_case', icon: ClipboardList },
]

const input = ref(typeof route.query.q === 'string' ? route.query.q : '')
const inputEl = ref<HTMLInputElement | null>(null)
const type = ref<SearchType | 'all'>(typeof route.query.type === 'string' && TYPES.some((x) => x.key === route.query.type) ? route.query.type as SearchType : 'all')
const projectId = ref<number | null>(typeof route.query.project === 'string' && route.query.project ? Number(route.query.project) : null)
const page = ref(0)

const projects = ref<Project[]>([])
const projectOptions = computed<SelectOption<number | null>[]>(() => [
  { value: null, label: t('shell.search.allProjects') },
  ...projects.value.map((p) => ({ value: p.id, label: p.name })),
])
const result = ref<SearchResponse | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)

const query = computed(() => input.value.trim())
const tooShort = computed(() => query.value.length > 0 && query.value.length < 2 && !/^#?\d+$/.test(query.value))

let seq = 0
async function run() {
  const q = query.value
  if (!q || tooShort.value) {
    result.value = null
    return
  }
  const my = ++seq
  loading.value = true
  error.value = null
  try {
    const r = await searchApi.search({
      q,
      types: type.value === 'all' ? [] : [type.value],
      projectId: projectId.value,
      page: page.value,
      size: PAGE_SIZE,
    })
    if (my === seq) result.value = r
  } catch (e: any) {
    if (my === seq) error.value = e?.data?.message ?? t('shell.search.failed')
  } finally {
    if (my === seq) loading.value = false
  }
}

// 입력 디바운스 + URL 동기화
let timer: ReturnType<typeof setTimeout> | null = null
watch(input, () => {
  if (timer) clearTimeout(timer)
  timer = setTimeout(() => {
    page.value = 0
    syncUrl()
    run()
  }, 250)
})
watch([type, projectId], () => {
  page.value = 0
  syncUrl()
  run()
})
watch(page, run)

function syncUrl() {
  router.replace({
    query: {
      q: query.value || undefined,
      type: type.value === 'all' ? undefined : type.value,
      project: projectId.value ?? undefined,
    },
  })
}

onMounted(async () => {
  inputEl.value?.focus()
  projectsApi.list().then((list) => { projects.value = list }).catch(() => { /* 필터만 비운다 */ })
  await run()
})

const totalPages = computed(() => (result.value ? Math.max(1, Math.ceil(result.value.total / PAGE_SIZE)) : 1))
const totalAll = computed(() => {
  const c = result.value?.counts ?? {}
  return Object.values(c).reduce((a, b) => a + (b ?? 0), 0)
})

function typeMeta(k: SearchType) {
  return TYPES.find((x) => x.key === k)!
}
function open(item: SearchItem) {
  router.push(searchItemPath(item))
}
</script>

<template>
  <section>
    <header class="mb-5">
      <h1 class="text-xl font-bold text-slate-800 md:text-2xl dark:text-slate-100">{{ $t('shell.search.title') }}</h1>
      <p class="mt-1 text-sm text-slate-500 dark:text-slate-400">{{ $t('shell.search.subtitle') }}</p>
    </header>

    <!-- 검색창 -->
    <div class="relative">
      <Search class="absolute left-4 top-1/2 h-5 w-5 -translate-y-1/2 text-slate-400 dark:text-slate-500" />
      <input
        ref="inputEl"
        v-model="input"
        type="search"
        :placeholder="$t('shell.search.placeholder')"
        class="w-full rounded-xl border border-slate-200 bg-white py-3 pl-12 pr-4 text-base shadow-sm focus:border-emerald-300 focus:outline-none focus:ring-2 focus:ring-emerald-200 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100 dark:placeholder-slate-500 dark:focus:ring-emerald-500/20"
      />
    </div>
    <p v-if="tooShort" class="mt-2 text-xs text-slate-400 dark:text-slate-500">{{ $t('shell.search.minLength') }}</p>

    <!-- 종류 탭 + 프로젝트 필터 -->
    <div class="mt-4 flex flex-wrap items-center gap-2">
      <div class="flex flex-wrap gap-1 rounded-lg bg-slate-100 p-1 dark:bg-slate-800">
        <button
          type="button"
          :class="[
            'rounded-md px-3 py-1.5 text-xs font-medium transition-colors',
            type === 'all' ? 'bg-white text-slate-800 shadow-sm dark:bg-slate-900 dark:text-slate-100' : 'text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-200',
          ]"
          @click="type = 'all'"
        >
          {{ $t('shell.search.types.all') }}
          <span v-if="result" class="ml-1 text-slate-400 dark:text-slate-500">{{ totalAll }}</span>
        </button>
        <button
          v-for="tp in TYPES"
          :key="tp.key"
          type="button"
          :class="[
            'rounded-md px-3 py-1.5 text-xs font-medium transition-colors',
            type === tp.key ? 'bg-white text-slate-800 shadow-sm dark:bg-slate-900 dark:text-slate-100' : 'text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-200',
          ]"
          @click="type = tp.key"
        >
          {{ $t(tp.label) }}
          <span v-if="result" class="ml-1 text-slate-400 dark:text-slate-500">{{ result.counts[tp.key] ?? 0 }}</span>
        </button>
      </div>
      <AppSelect v-model="projectId" class="ml-auto min-w-[10rem] max-w-[16rem]" size="md" rounded="lg" :options="projectOptions" />
    </div>

    <!-- 결과 -->
    <div class="mt-4 overflow-hidden rounded-xl border border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-900">
      <div v-if="!query" class="px-4 py-14 text-center text-sm text-slate-400 dark:text-slate-500">
        <Search class="mx-auto mb-2 h-6 w-6 text-slate-300 dark:text-slate-600" />
        {{ $t('shell.search.hint') }}
      </div>
      <div v-else-if="loading && !result" class="px-4 py-14 text-center text-sm text-slate-400 dark:text-slate-500">{{ $t('common.state.loading') }}</div>
      <div v-else-if="error" class="px-4 py-6 text-sm text-red-700 dark:text-red-400">{{ error }}</div>
      <template v-else-if="result">
        <div class="flex items-center justify-between border-b border-slate-100 px-4 py-2 text-xs text-slate-400 dark:border-slate-800 dark:text-slate-500">
          <span>{{ $t('shell.search.resultCount', { n: result.total.toLocaleString() }) }}</span>
          <span v-if="loading">{{ $t('common.state.loading') }}</span>
        </div>
        <ul v-if="result.items.length > 0" class="divide-y divide-slate-100 dark:divide-slate-800">
          <li v-for="item in result.items" :key="`${item.type}-${item.id}`">
            <button type="button" class="flex w-full items-start gap-3 px-4 py-3 text-left transition hover:bg-slate-50 dark:hover:bg-slate-800/60" @click="open(item)">
              <span class="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-slate-100 text-slate-500 dark:bg-slate-800 dark:text-slate-400">
                <component :is="typeMeta(item.type).icon" class="h-4 w-4" />
              </span>
              <span class="min-w-0 flex-1">
                <span class="flex flex-wrap items-center gap-2">
                  <span class="rounded bg-slate-100 px-1.5 py-0.5 text-[11px] font-medium text-slate-500 dark:bg-slate-800 dark:text-slate-400">{{ $t(typeMeta(item.type).label) }}</span>
                  <span v-if="item.type === 'qa'" class="text-xs tabular-nums text-slate-400 dark:text-slate-500">#{{ item.id }}</span>
                  <span class="line-clamp-1 text-sm font-medium text-slate-800 dark:text-slate-100" v-html="highlight(item.title, result.query)" />
                  <StatusBadge v-if="item.status && item.type !== 'test_case'" :status="item.status as any" />
                </span>
                <span v-if="item.snippet" class="mt-1 line-clamp-2 block text-xs text-slate-500 dark:text-slate-400" v-html="highlight(item.snippet, result.query)" />
                <span class="mt-1 block text-[11px] text-slate-400 dark:text-slate-500">
                  <template v-if="item.projectName">{{ item.projectName }}</template>
                  <template v-if="item.updatedAt"> · {{ item.updatedAt.slice(0, 10) }}</template>
                </span>
              </span>
            </button>
          </li>
        </ul>
        <div v-else class="px-4 py-14 text-center text-sm text-slate-400 dark:text-slate-500">
          <Inbox class="mx-auto mb-2 h-6 w-6 text-slate-300 dark:text-slate-600" />
          {{ $t('shell.search.empty') }}
        </div>
      </template>
    </div>

    <div v-if="result && totalPages > 1" class="mt-3 flex items-center justify-end gap-1">
      <button
        type="button"
        class="rounded-md border border-slate-200 px-3 py-1.5 text-xs font-medium text-slate-600 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-800/60"
        :disabled="page === 0"
        @click="page--"
      >{{ $t('dashboard.qaList.prev') }}</button>
      <span class="px-2 text-xs text-slate-500 dark:text-slate-400">{{ page + 1 }} / {{ totalPages }}</span>
      <button
        type="button"
        class="rounded-md border border-slate-200 px-3 py-1.5 text-xs font-medium text-slate-600 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-800/60"
        :disabled="page >= totalPages - 1"
        @click="page++"
      >{{ $t('dashboard.qaList.next') }}</button>
    </div>
  </section>
</template>
