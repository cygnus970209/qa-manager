<script setup lang="ts" generic="T">
import { Check, ChevronDown, X } from '@lucide/vue'

/**
 * 검색형 단일 select.
 * - 텍스트 입력으로 옵션 필터링 (label 부분 매칭, 대소문자 무시)
 * - ↑/↓ 로 이동, Enter 로 선택, Esc 로 닫기
 * - clearable 이면 X 버튼으로 미선택 가능
 *
 * 드롭다운 패널은 Teleport(body) + fixed 로 띄워서 모달/overflow-hidden 컨테이너 안에서도
 * 잘리지 않는다. 화면 아래 공간이 부족하면 위로 펼친다.
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
const triggerEl = ref<HTMLButtonElement | null>(null)
const panelEl = ref<HTMLElement | null>(null)
const inputEl = ref<HTMLInputElement | null>(null)
const panelStyle = ref<Record<string, string>>({})

const selected = computed<T | null>(() => {
  if (props.modelValue == null) return null
  return props.options.find((o) => props.keyFn(o) === props.modelValue) ?? null
})

const { t } = useI18n()

const displayText = computed(() => {
  if (selected.value) return props.labelFn(selected.value)
  return props.emptyLabel ?? t('common.select.notSelected')
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

/** 트리거 위치를 측정해 패널을 화면 좌표(fixed)에 배치. 아래 공간이 부족하면 위로. */
function updatePosition() {
  const el = triggerEl.value
  if (!el) return
  const r = el.getBoundingClientRect()
  const gap = 4
  const maxPanel = 288
  const below = window.innerHeight - r.bottom - gap
  const above = r.top - gap
  const openUp = below < Math.min(maxPanel, 220) && above > below
  const maxHeight = Math.max(140, Math.min(maxPanel, openUp ? above : below))
  panelStyle.value = openUp
    ? {
        position: 'fixed',
        left: `${r.left}px`,
        width: `${r.width}px`,
        bottom: `${window.innerHeight - r.top + gap}px`,
        maxHeight: `${maxHeight}px`,
      }
    : {
        position: 'fixed',
        left: `${r.left}px`,
        width: `${r.width}px`,
        top: `${r.bottom + gap}px`,
        maxHeight: `${maxHeight}px`,
      }
}

function openDropdown() {
  if (props.disabled) return
  query.value = ''
  highlighted.value = 0
  // 먼저 트리거 기준으로 panelStyle 을 fixed 좌표로 계산한 뒤 open.
  // 안 그러면 첫 렌더에서 panel 이 body 끝에 static 으로 잠깐 붙어 페이지가 늘어나며 스크롤이 튄다.
  updatePosition()
  open.value = true
  nextTick(() => {
    updatePosition()
    // preventScroll 없이 focus 하면 일부 브라우저가 패널 위치로 스크롤하려고 함.
    inputEl.value?.focus({ preventScroll: true })
  })
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
    const o = list[highlighted.value]
    if (o) selectOption(o)
    e.preventDefault()
  } else if (e.key === 'Escape') {
    close()
    e.preventDefault()
  }
}

// 열려 있는 동안 스크롤/리사이즈 시 위치 추적 (capture 로 모달 내부 스크롤 컨테이너도 포함)
watch(open, (v) => {
  if (v) {
    window.addEventListener('scroll', updatePosition, true)
    window.addEventListener('resize', updatePosition)
  } else {
    window.removeEventListener('scroll', updatePosition, true)
    window.removeEventListener('resize', updatePosition)
  }
})

function onDocClick(e: MouseEvent) {
  if (!open.value) return
  const t = e.target as Node
  if (rootEl.value?.contains(t)) return
  if (panelEl.value?.contains(t)) return // teleport 된 패널 내부 클릭은 유지
  close()
}
onMounted(() => document.addEventListener('mousedown', onDocClick))
onBeforeUnmount(() => {
  document.removeEventListener('mousedown', onDocClick)
  window.removeEventListener('scroll', updatePosition, true)
  window.removeEventListener('resize', updatePosition)
})
</script>

<template>
  <div ref="rootEl" class="relative">
    <button
      ref="triggerEl"
      type="button"
      :disabled="disabled"
      class="flex w-full items-center justify-between rounded-md border border-slate-300 bg-white px-3 py-1.5 text-left text-xs focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500 disabled:cursor-not-allowed disabled:bg-slate-50 dark:border-slate-700 dark:bg-slate-900 dark:disabled:bg-slate-800/60"
      @click="open ? close() : openDropdown()"
    >
      <span :class="[selected ? 'text-slate-700 dark:text-slate-200' : 'text-slate-400 dark:text-slate-500']">{{ displayText }}</span>
      <span class="flex items-center gap-1">
        <button
          v-if="clearable && selected && !disabled"
          type="button"
          class="rounded p-0.5 text-slate-400 hover:bg-slate-100 hover:text-slate-600 dark:hover:bg-slate-700 dark:hover:text-slate-300"
          :aria-label="$t('common.select.clearSelection')"
          @click.stop="clear"
        >
          <X class="h-3 w-3" />
        </button>
        <ChevronDown class="h-3.5 w-3.5 text-slate-400" :class="open && 'rotate-180'" />
      </span>
    </button>

    <Teleport to="body">
      <div
        v-if="open"
        ref="panelEl"
        :style="panelStyle"
        class="z-[100] flex flex-col overflow-hidden rounded-md border border-slate-200 bg-white shadow-lg dark:border-slate-700 dark:bg-slate-900"
      >
        <input
          ref="inputEl"
          v-model="query"
          type="text"
          :placeholder="placeholder ?? $t('common.select.searchPlaceholder')"
          class="w-full shrink-0 border-b border-slate-100 bg-transparent px-3 py-2 text-xs focus:outline-none dark:border-slate-800 dark:text-slate-100 dark:placeholder-slate-500"
          @keydown="onKey"
        />
        <ul class="flex-1 overflow-y-auto py-1">
          <li v-if="filtered.length === 0" class="px-3 py-2 text-xs text-slate-400 dark:text-slate-500">{{ $t('common.select.noResults') }}</li>
          <li v-for="(o, i) in filtered" :key="String(keyFn(o))">
            <button
              type="button"
              :class="[
                'flex w-full items-center justify-between px-3 py-1.5 text-left text-xs transition-colors',
                i === highlighted ? 'bg-emerald-50 text-emerald-700 dark:bg-emerald-500/10 dark:text-emerald-400' : 'text-slate-700 hover:bg-slate-50 dark:text-slate-200 dark:hover:bg-slate-800/60',
                modelValue === keyFn(o) ? 'font-medium' : '',
              ]"
              @mousedown.prevent="selectOption(o)"
              @mouseenter="highlighted = i"
            >
              <span class="truncate">{{ labelFn(o) }}</span>
              <Check v-if="modelValue === keyFn(o)" class="h-3 w-3 text-emerald-500 dark:text-emerald-400" />
            </button>
          </li>
        </ul>
      </div>
    </Teleport>
  </div>
</template>
