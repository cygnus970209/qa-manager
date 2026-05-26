<script setup lang="ts" generic="T">
import { Check, ChevronDown, X } from '@lucide/vue'

/**
 * 검색형 단일 select.
 * - 텍스트 입력으로 옵션 필터링 (label 부분 매칭, 대소문자 무시)
 * - ↑/↓ 로 이동, Enter 로 선택, Esc 로 닫기
 * - clearable 이면 X 버튼으로 미선택 가능
 *
 * 옵션은 임의 타입 T 의 배열로 받고, `keyFn` / `labelFn` 으로 식별/표시한다.
 * 값(modelValue) 은 keyFn 결과의 타입.
 */
const props = defineProps<{
  modelValue: string | number | null
  options: T[]
  keyFn: (o: T) => string | number
  labelFn: (o: T) => string
  /** 보조 검색 키워드 (예: 역할/메모) */
  searchFn?: (o: T) => string
  placeholder?: string
  /** 미선택 옵션을 허용할지. true 면 우측 X 버튼 노출 */
  clearable?: boolean
  /** 빈 값 표기 (예: "미지정") */
  emptyLabel?: string
  disabled?: boolean
}>()
const emit = defineEmits<{
  'update:modelValue': [value: string | number | null]
}>()

const open = ref(false)
const query = ref('')
const highlighted = ref(0)
const rootEl = ref<HTMLElement | null>(null)
const inputEl = ref<HTMLInputElement | null>(null)

const selected = computed<T | null>(() => {
  if (props.modelValue == null) return null
  return props.options.find((o) => props.keyFn(o) === props.modelValue) ?? null
})

const displayText = computed(() => {
  if (selected.value) return props.labelFn(selected.value)
  return props.emptyLabel ?? '선택 안함'
})

const filtered = computed<T[]>(() => {
  const q = query.value.trim().toLowerCase()
  if (!q) return props.options
  return props.options.filter((o) => {
    const hay = (props.labelFn(o) + ' ' + (props.searchFn?.(o) ?? '')).toLowerCase()
    return hay.includes(q)
  })
})

watch(filtered, () => { highlighted.value = 0 })

function openDropdown() {
  if (props.disabled) return
  open.value = true
  query.value = ''
  highlighted.value = 0
  nextTick(() => inputEl.value?.focus())
}

function close() {
  open.value = false
  query.value = ''
}

function selectOption(o: T) {
  emit('update:modelValue', props.keyFn(o))
  close()
}

function clear(e?: Event) {
  e?.stopPropagation()
  emit('update:modelValue', null)
  close()
}

function onKey(e: KeyboardEvent) {
  if (!open.value) return
  const list = filtered.value
  if (e.key === 'ArrowDown') {
    highlighted.value = Math.min(highlighted.value + 1, list.length - 1)
    e.preventDefault()
  } else if (e.key === 'ArrowUp') {
    highlighted.value = Math.max(highlighted.value - 1, 0)
    e.preventDefault()
  } else if (e.key === 'Enter') {
    if (list[highlighted.value]) selectOption(list[highlighted.value])
    e.preventDefault()
  } else if (e.key === 'Escape') {
    close()
    e.preventDefault()
  }
}

function onDocClick(e: MouseEvent) {
  if (!open.value) return
  if (rootEl.value && rootEl.value.contains(e.target as Node)) return
  close()
}
onMounted(() => document.addEventListener('mousedown', onDocClick))
onBeforeUnmount(() => document.removeEventListener('mousedown', onDocClick))
</script>

<template>
  <div ref="rootEl" class="relative">
    <button
      type="button"
      :disabled="disabled"
      class="flex w-full items-center justify-between rounded-md border border-slate-300 bg-white px-3 py-1.5 text-left text-xs focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500 disabled:cursor-not-allowed disabled:bg-slate-50"
      @click="open ? close() : openDropdown()"
    >
      <span :class="[selected ? 'text-slate-700' : 'text-slate-400']">{{ displayText }}</span>
      <span class="flex items-center gap-1">
        <button
          v-if="clearable && selected && !disabled"
          type="button"
          class="rounded p-0.5 text-slate-400 hover:bg-slate-100 hover:text-slate-600"
          aria-label="선택 해제"
          @click.stop="clear"
        >
          <X class="h-3 w-3" />
        </button>
        <ChevronDown class="h-3.5 w-3.5 text-slate-400" :class="open && 'rotate-180'" />
      </span>
    </button>

    <div
      v-if="open"
      class="absolute left-0 right-0 top-full z-30 mt-1 overflow-hidden rounded-md border border-slate-200 bg-white shadow-lg"
    >
      <input
        ref="inputEl"
        v-model="query"
        type="text"
        :placeholder="placeholder ?? '검색...'"
        class="w-full border-b border-slate-100 px-3 py-2 text-xs focus:outline-none"
        @keydown="onKey"
      />
      <ul class="max-h-60 overflow-y-auto py-1">
        <li v-if="filtered.length === 0" class="px-3 py-2 text-xs text-slate-400">결과 없음</li>
        <li v-for="(o, i) in filtered" :key="String(keyFn(o))">
          <button
            type="button"
            :class="[
              'flex w-full items-center justify-between px-3 py-1.5 text-left text-xs transition-colors',
              i === highlighted ? 'bg-emerald-50 text-emerald-700' : 'text-slate-700 hover:bg-slate-50',
              modelValue === keyFn(o) ? 'font-medium' : '',
            ]"
            @mousedown.prevent="selectOption(o)"
            @mouseenter="highlighted = i"
          >
            <span class="truncate">{{ labelFn(o) }}</span>
            <Check v-if="modelValue === keyFn(o)" class="h-3 w-3 text-emerald-500" />
          </button>
        </li>
      </ul>
    </div>
  </div>
</template>
