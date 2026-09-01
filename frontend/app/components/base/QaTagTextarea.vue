<script setup lang="ts">
import { Check } from '@lucide/vue'
import type { QaItem } from '~/types/api'

/**
 * `#` 입력 시 QA 태그 자동완성 드롭다운이 붙는 textarea.
 * rows/maxlength/placeholder/class/@paste 등은 attrs 로 그대로 textarea 에 전달된다.
 */
defineOptions({ inheritAttrs: false })

const props = defineProps<{
  modelValue: string
  /** # 자동완성 후보 목록. 미전달 시 첫 트리거에서 전체 QA 목록을 지연 로드한다. */
  qaItems?: QaItem[]
  /** 후보에서 제외할 QA id (자기 자신 태그 방지). */
  excludeId?: number
}>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

const qaApi = useQa()

const rootRef = ref<HTMLElement | null>(null)
const el = ref<HTMLTextAreaElement | null>(null)

const show = ref(false)
const query = ref('')
const idx = ref(0)
const above = ref(false)
const DROPDOWN_HEIGHT = 192 // max-h-48 = 12rem

/** qaItems 미전달 시 지연 로드 캐시 (컴포넌트 생명주기 동안 1회). */
const lazyItems = ref<QaItem[] | null>(null)
let lazyLoading = false
async function ensureItems() {
  if (props.qaItems || lazyItems.value || lazyLoading) return
  lazyLoading = true
  try {
    lazyItems.value = await qaApi.list()
  } catch {
    lazyItems.value = []
  } finally {
    lazyLoading = false
  }
}

const candidates = computed(() => props.qaItems ?? lazyItems.value ?? [])
const filtered = computed(() => {
  const q = query.value.toLowerCase()
  return candidates.value
    .filter((it) => it.id !== props.excludeId)
    .filter((it) => q === '' || String(it.id).includes(q) || it.title.toLowerCase().includes(q))
    .slice(0, 8)
})

watch(idx, () => {
  if (!show.value) return
  nextTick(() => {
    rootRef.value?.querySelector('[data-active="true"]')?.scrollIntoView({ block: 'nearest' })
  })
})

/** 커서 앞 `#검색어` 트리거 감지 (댓글 멘션과 동일 패턴). */
function check(t: HTMLTextAreaElement) {
  const cursor = t.selectionStart ?? 0
  const before = t.value.slice(0, cursor)
  const lastHash = before.lastIndexOf('#')
  if (lastHash === -1) { show.value = false; return }
  const after = before.slice(lastHash + 1)
  if (/\s/.test(after)) { show.value = false; return }
  ensureItems()
  query.value = after
  idx.value = 0
  show.value = true
  // 아래 공간이 부족하면 위로 띄운다.
  const rect = t.getBoundingClientRect()
  const spaceBelow = window.innerHeight - rect.bottom
  above.value = spaceBelow < DROPDOWN_HEIGHT && rect.top > spaceBelow
}

function onInput(e: Event) {
  const t = e.target as HTMLTextAreaElement
  emit('update:modelValue', t.value)
  check(t)
}

/** # 뒤 검색어를 선택한 QA 의 `#번호 ` 로 치환. */
function insert(q: QaItem) {
  const t = el.value
  if (!t) return
  const value = t.value
  const cursor = t.selectionStart ?? 0
  const before = value.slice(0, cursor)
  const lastHash = before.lastIndexOf('#')
  if (lastHash === -1) return
  const beforeHash = value.slice(0, lastHash)
  const next = `${beforeHash}#${q.id} ${value.slice(cursor)}`
  emit('update:modelValue', next)
  show.value = false
  nextTick(() => {
    const pos = beforeHash.length + String(q.id).length + 2
    t.setSelectionRange(pos, pos)
    t.focus()
  })
}

function onKeydown(e: KeyboardEvent) {
  if (!show.value || filtered.value.length === 0) return
  if (e.key === 'Escape') { show.value = false; e.preventDefault() }
  else if (e.key === 'Enter') {
    const q = filtered.value[idx.value]
    if (q) { insert(q); e.preventDefault() }
  }
  else if (e.key === 'ArrowDown') {
    idx.value = Math.min(idx.value + 1, filtered.value.length - 1)
    e.preventDefault()
  }
  else if (e.key === 'ArrowUp') {
    idx.value = Math.max(idx.value - 1, 0)
    e.preventDefault()
  }
}

/* 바깥 클릭으로 드롭다운 닫기 */
function onDocMousedown(e: MouseEvent) {
  if (!show.value) return
  if (rootRef.value?.contains(e.target as Node)) return
  show.value = false
}
onMounted(() => document.addEventListener('mousedown', onDocMousedown))
onBeforeUnmount(() => document.removeEventListener('mousedown', onDocMousedown))
</script>

<template>
  <div ref="rootRef" class="relative">
    <textarea
      ref="el"
      :value="modelValue"
      v-bind="$attrs"
      @input="onInput"
      @keydown="onKeydown"
      @click="check($event.target as HTMLTextAreaElement)"
    />
    <ul
      v-if="show && filtered.length > 0"
      :class="['absolute left-0 z-50 max-h-48 w-72 overflow-y-auto rounded-lg border border-slate-200 bg-white shadow-lg dark:border-slate-700 dark:bg-slate-900', above ? 'bottom-full mb-1' : 'top-full mt-1']"
    >
      <li v-for="(qi, i) in filtered" :key="qi.id">
        <button
          type="button"
          :data-active="i === idx"
          :class="['flex w-full items-center gap-2 px-3 py-2 text-left transition-colors hover:bg-slate-50 dark:hover:bg-slate-800/60', i === idx ? 'bg-blue-50 dark:bg-blue-500/10' : '']"
          @mousedown.prevent
          @click="insert(qi)"
        >
          <span class="shrink-0 font-mono text-[11px] font-medium text-blue-500 dark:text-blue-400">#{{ qi.id }}</span>
          <span class="min-w-0 flex-1 truncate text-sm text-slate-700 dark:text-slate-200">{{ qi.title }}</span>
          <Check v-if="i === idx" class="h-3 w-3 text-blue-500 dark:text-blue-400" />
        </button>
      </li>
    </ul>
  </div>
</template>
