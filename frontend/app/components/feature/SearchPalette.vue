<script setup lang="ts">
import { Bug, ClipboardList, CornerDownLeft, Folder, MessageSquare, Search, Tag } from '@lucide/vue'
import { highlight, searchItemPath } from '~/composables/useSearch'
import type { SearchItem, SearchType } from '~/types/api'

/**
 * ⌘K / Ctrl+K 검색 팔레트. 같은 검색 API 를 8건까지만 불러 종류별로 묶어 보여준다.
 * ↑↓ 이동, Enter 열기, Esc 닫기. 맨 아래 "모든 결과 보기"는 /search 로.
 * 열림 상태는 useState('search-palette-open') — 사이드바·단축키가 함께 쓴다.
 */
const open = useState<boolean>('search-palette-open', () => false)
const router = useRouter()
const searchApi = useSearch()

const ICONS: Record<SearchType, unknown> = { qa: Bug, comment: MessageSquare, project: Folder, update: Tag, test_case: ClipboardList }
const ORDER: SearchType[] = ['qa', 'comment', 'project', 'update', 'test_case']

const input = ref('')
const inputEl = ref<HTMLInputElement | null>(null)
const items = ref<SearchItem[]>([])
const total = ref(0)
const loading = ref(false)
const active = ref(0)

const query = computed(() => input.value.trim())
/** 종류별 묶음 (표시 순서 유지) */
const groups = computed(() => ORDER
  .map((type) => ({ type, items: items.value.filter((i) => i.type === type) }))
  .filter((g) => g.items.length > 0))
/** 키보드 이동용 평탄 목록 (묶음 순서대로) */
const flat = computed(() => groups.value.flatMap((g) => g.items))

let seq = 0
let timer: ReturnType<typeof setTimeout> | null = null
watch(input, () => {
  if (timer) clearTimeout(timer)
  timer = setTimeout(run, 200)
})
async function run() {
  const q = query.value
  if (q.length < 2 && !/^#?\d+$/.test(q)) {
    items.value = []
    total.value = 0
    return
  }
  const my = ++seq
  loading.value = true
  try {
    const r = await searchApi.search({ q, size: 8 })
    if (my !== seq) return
    items.value = r.items
    total.value = r.total
    active.value = 0
  } catch {
    if (my === seq) { items.value = []; total.value = 0 }
  } finally {
    if (my === seq) loading.value = false
  }
}

watch(open, (v) => {
  if (v) {
    input.value = ''
    items.value = []
    total.value = 0
    active.value = 0
    nextTick(() => inputEl.value?.focus())
  }
})

function close() {
  open.value = false
}
function go(item: SearchItem) {
  close()
  router.push(searchItemPath(item))
}
function goAll() {
  close()
  router.push({ path: '/search', query: { q: query.value } })
}
function onKeydown(e: KeyboardEvent) {
  if (e.key === 'ArrowDown') {
    e.preventDefault()
    active.value = Math.min(active.value + 1, flat.value.length) // 마지막 = "모든 결과 보기"
  } else if (e.key === 'ArrowUp') {
    e.preventDefault()
    active.value = Math.max(active.value - 1, 0)
  } else if (e.key === 'Enter') {
    e.preventDefault()
    const item = flat.value[active.value]
    if (item) go(item)
    else if (query.value) goAll()
  } else if (e.key === 'Escape') {
    e.preventDefault()
    close()
  }
}
function indexOf(item: SearchItem) {
  return flat.value.indexOf(item)
}
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="fixed inset-0 z-50 flex items-start justify-center bg-slate-900/40 px-4 pt-[12vh]" @mousedown.self="close">
      <div class="w-full max-w-xl overflow-hidden rounded-xl border border-slate-200 bg-white shadow-2xl dark:border-slate-700 dark:bg-slate-900" role="dialog" :aria-label="$t('shell.search.title')">
        <div class="flex items-center gap-3 border-b border-slate-100 px-4 dark:border-slate-800">
          <Search class="h-5 w-5 shrink-0 text-slate-400 dark:text-slate-500" />
          <input
            ref="inputEl"
            v-model="input"
            type="text"
            :placeholder="$t('shell.search.placeholder')"
            class="h-12 w-full bg-transparent text-base text-slate-800 placeholder-slate-400 focus:outline-none dark:text-slate-100 dark:placeholder-slate-500"
            @keydown="onKeydown"
          />
          <kbd class="hidden rounded border border-slate-200 px-1.5 py-0.5 text-[10px] font-medium text-slate-400 dark:border-slate-700 dark:text-slate-500 sm:block">ESC</kbd>
        </div>

        <div class="max-h-[60vh] overflow-y-auto">
          <p v-if="!query" class="px-4 py-8 text-center text-sm text-slate-400 dark:text-slate-500">{{ $t('shell.search.paletteHint') }}</p>
          <p v-else-if="!loading && flat.length === 0" class="px-4 py-8 text-center text-sm text-slate-400 dark:text-slate-500">{{ $t('shell.search.empty') }}</p>
          <template v-else>
            <div v-for="g in groups" :key="g.type" class="py-1">
              <p class="px-4 pb-1 pt-2 text-[11px] font-semibold uppercase tracking-wider text-slate-400 dark:text-slate-500">{{ $t(`shell.search.types.${g.type}`) }}</p>
              <button
                v-for="item in g.items"
                :key="`${item.type}-${item.id}`"
                type="button"
                :class="[
                  'flex w-full items-start gap-3 px-4 py-2 text-left',
                  indexOf(item) === active ? 'bg-emerald-50 dark:bg-emerald-500/10' : 'hover:bg-slate-50 dark:hover:bg-slate-800/60',
                ]"
                @mouseenter="active = indexOf(item)"
                @click="go(item)"
              >
                <component :is="ICONS[item.type]" class="mt-0.5 h-4 w-4 shrink-0 text-slate-400 dark:text-slate-500" />
                <span class="min-w-0 flex-1">
                  <span class="line-clamp-1 text-sm text-slate-800 dark:text-slate-100">
                    <span v-if="item.type === 'qa'" class="mr-1 tabular-nums text-slate-400 dark:text-slate-500">#{{ item.id }}</span>
                    <span v-html="highlight(item.title, query)" />
                  </span>
                  <span v-if="item.snippet" class="line-clamp-1 block text-xs text-slate-500 dark:text-slate-400" v-html="highlight(item.snippet, query)" />
                </span>
                <span v-if="item.projectName" class="shrink-0 text-[11px] text-slate-400 dark:text-slate-500">{{ item.projectName }}</span>
              </button>
            </div>
          </template>
        </div>

        <button
          v-if="query"
          type="button"
          :class="[
            'flex w-full items-center justify-between border-t border-slate-100 px-4 py-2.5 text-xs dark:border-slate-800',
            active === flat.length ? 'bg-emerald-50 text-emerald-700 dark:bg-emerald-500/10 dark:text-emerald-400' : 'text-slate-500 hover:bg-slate-50 dark:text-slate-400 dark:hover:bg-slate-800/60',
          ]"
          @mouseenter="active = flat.length"
          @click="goAll"
        >
          <span>{{ $t('shell.search.showAll', { n: total.toLocaleString() }) }}</span>
          <CornerDownLeft class="h-3.5 w-3.5" />
        </button>
      </div>
    </div>
  </Teleport>
</template>
