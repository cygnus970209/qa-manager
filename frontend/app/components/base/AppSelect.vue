<script setup lang="ts" generic="T, V extends string | number | null">
import { Check, ChevronDown, X } from '@lucide/vue'

/**
 * 앱 공통 셀렉트. 네이티브 <select> 대신 쓴다 — OS 마다 다르게 그려지는 드롭다운 대신
 * 트리거·옵션 목록을 앱 스타일(크기·색·다크모드)로 통일한다.
 *
 * - options 는 { value, label } 배열이 기본. 다른 모양(멤버 등)이면 keyFn/labelFn 으로 꺼낸다.
 * - value 로 null 을 쓸 수 있다("전체", "미분류" 같은 항목). 목록에 없는 값이면 placeholder 를 보인다.
 * - searchable: 패널 상단에 검색 입력(옵션이 많을 때). 아니어도 ↑/↓ 이동, Enter 선택, Esc 닫기는 된다.
 * - clearable: 우측 X(또는 Backspace/Delete)로 null 을 선택.
 * - size: xs(배지 옆 인라인) / sm(필터·툴바) / md(폼 입력과 같은 높이)
 * - variant plain: 테두리·배경 없이 triggerClass 로만 꾸민다(관리자 화면의 상태 알약).
 *
 * 패널은 Teleport(body) + fixed 로 띄워서 모달/overflow-hidden 컨테이너 안에서도 잘리지 않는다.
 * 아래 공간이 부족하면 위로 펼치고, 오른쪽 공간이 부족하면 트리거 오른쪽 끝에 맞춘다.
 */
type PlainOption = { value: V; label: string }

const props = withDefaults(defineProps<{
  modelValue: V
  options: T[]
  keyFn?: (o: T) => V
  labelFn?: (o: T) => string
  /** 보조 검색 키워드 (예: 역할/메모) — searchable 일 때만 쓰인다 */
  searchFn?: (o: T) => string
  searchable?: boolean
  searchPlaceholder?: string
  /** 선택된 옵션이 없을 때 트리거에 보일 글자 */
  placeholder?: string
  /** 미선택(null)을 허용할지. true 면 우측 X 버튼 노출 */
  clearable?: boolean
  disabled?: boolean
  size?: 'xs' | 'sm' | 'md'
  rounded?: 'md' | 'lg'
  variant?: 'outline' | 'plain'
  /** 트리거 button 에 덧붙일 클래스 (plain 이면 이걸로 전부 꾸민다) */
  triggerClass?: string
}>(), {
  keyFn: undefined,
  labelFn: undefined,
  searchFn: undefined,
  searchPlaceholder: undefined,
  placeholder: undefined,
  size: 'sm',
  rounded: 'md',
  variant: 'outline',
  triggerClass: '',
})
const emit = defineEmits<{
  'update:modelValue': [value: V]
}>()

const { t } = useI18n()

const open = ref(false)
const query = ref('')
const highlighted = ref(0)
const rootEl = ref<HTMLElement | null>(null)
const triggerEl = ref<HTMLButtonElement | null>(null)
const panelEl = ref<HTMLElement | null>(null)
const listEl = ref<HTMLElement | null>(null)
const inputEl = ref<HTMLInputElement | null>(null)
const panelStyle = ref<Record<string, string>>({})
/** 열 때 한 번 잰 패널의 자연 폭 — 스크롤/리사이즈 때 좌우 정렬 판단에 재사용 */
let panelNaturalWidth: number | null = null

function keyOf(o: T): V {
  return props.keyFn ? props.keyFn(o) : (o as unknown as PlainOption).value
}
function labelOf(o: T): string {
  return props.labelFn ? props.labelFn(o) : (o as unknown as PlainOption).label
}
function isSelected(o: T) {
  return keyOf(o) === props.modelValue
}

const selected = computed<T | null>(() => props.options.find((o) => isSelected(o)) ?? null)

const displayText = computed(() => {
  if (selected.value) return labelOf(selected.value)
  return props.placeholder ?? t('common.select.notSelected')
})

const filtered = computed<T[]>(() => {
  const q = query.value.trim().toLowerCase()
  if (!q) return props.options
  return props.options.filter((o) => {
    const hay = (labelOf(o) + ' ' + (props.searchFn?.(o) ?? '')).toLowerCase()
    return hay.includes(q)
  })
})

watch(filtered, () => { highlighted.value = 0 })
watch(highlighted, () => {
  nextTick(() => listEl.value?.querySelector<HTMLElement>('[data-active="true"]')?.scrollIntoView({ block: 'nearest' }))
})

/** 트리거 위치를 재서 패널을 화면 좌표(fixed)에 놓는다. */
function updatePosition() {
  const el = triggerEl.value
  if (!el) return
  const r = el.getBoundingClientRect()
  const gap = 4
  const margin = 8
  const maxPanel = 288
  const vw = window.innerWidth
  const vh = window.innerHeight

  const below = vh - r.bottom - gap
  const above = r.top - gap
  const openUp = below < Math.min(maxPanel, 220) && above > below
  const maxHeight = Math.max(140, Math.min(maxPanel, openUp ? above : below))

  // 폭은 트리거 이상·내용만큼(max-content). 왼쪽 정렬로 안 들어가고 오른쪽 정렬이 더 넓으면 오른쪽 끝에 맞춘다.
  const roomLeftAligned = vw - margin - r.left
  const roomRightAligned = r.right - margin
  const natural = panelNaturalWidth ?? r.width
  const alignRight = natural > roomLeftAligned && roomRightAligned > roomLeftAligned
  const maxWidth = Math.min(360, panelNaturalWidth == null ? vw - margin * 2 : alignRight ? roomRightAligned : roomLeftAligned)

  const style: Record<string, string> = {
    position: 'fixed',
    minWidth: `${r.width}px`,
    maxWidth: `${Math.max(r.width, maxWidth)}px`,
    maxHeight: `${maxHeight}px`,
  }
  if (alignRight) style.right = `${vw - r.right}px`
  else style.left = `${r.left}px`
  if (openUp) style.bottom = `${vh - r.top + gap}px`
  else style.top = `${r.bottom + gap}px`
  panelStyle.value = style
}

function openDropdown() {
  if (props.disabled) return
  query.value = ''
  panelNaturalWidth = null
  const idx = selected.value ? props.options.indexOf(selected.value) : -1
  highlighted.value = idx >= 0 ? idx : 0
  // 먼저 트리거 기준으로 panelStyle 을 fixed 좌표로 계산한 뒤 open.
  // 안 그러면 첫 렌더에서 panel 이 body 끝에 static 으로 잠깐 붙어 페이지가 늘어나며 스크롤이 튄다.
  updatePosition()
  open.value = true
  nextTick(() => {
    panelNaturalWidth = panelEl.value?.getBoundingClientRect().width ?? null
    updatePosition()
    listEl.value?.querySelector<HTMLElement>('[data-active="true"]')?.scrollIntoView({ block: 'nearest' })
    // preventScroll 없이 focus 하면 일부 브라우저가 패널 위치로 스크롤하려고 함.
    if (props.searchable) inputEl.value?.focus({ preventScroll: true })
  })
}

function close() {
  open.value = false
  query.value = ''
}

function selectOption(o: T) {
  emit('update:modelValue', keyOf(o))
  close()
}

function clear(e?: Event) {
  e?.stopPropagation()
  emit('update:modelValue', null as V)
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
  } else if (e.key === 'Tab') {
    close()
  }
}

/** 트리거에 포커스가 있을 때: 닫힌 상태면 ↑/↓ 로 열고, 열린 상태면 목록 탐색. */
function onTriggerKey(e: KeyboardEvent) {
  if (props.disabled) return
  if (!open.value) {
    if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
      e.preventDefault()
      openDropdown()
    } else if (props.clearable && selected.value && (e.key === 'Backspace' || e.key === 'Delete')) {
      e.preventDefault()
      clear()
    }
    return
  }
  onKey(e)
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
  const target = e.target as Node
  if (rootEl.value?.contains(target)) return
  if (panelEl.value?.contains(target)) return // teleport 된 패널 내부 클릭은 유지
  close()
}
onMounted(() => document.addEventListener('mousedown', onDocClick))
onBeforeUnmount(() => {
  document.removeEventListener('mousedown', onDocClick)
  window.removeEventListener('scroll', updatePosition, true)
  window.removeEventListener('resize', updatePosition)
})

/* ─── 클래스 ─── */
const sizeCls = { xs: 'px-2 py-1 text-xs', sm: 'px-2.5 py-1.5 text-xs', md: 'px-3 py-2 text-sm' } as const
const iconCls = { xs: 'h-3 w-3', sm: 'h-3.5 w-3.5', md: 'h-4 w-4' } as const
const triggerClasses = computed(() => [
  'flex w-full items-center justify-between gap-1.5 text-left focus:outline-none',
  props.variant === 'outline'
    ? [
        'border bg-white focus:border-emerald-500 focus:ring-1 focus:ring-emerald-500 disabled:cursor-not-allowed disabled:bg-slate-50 dark:bg-slate-900 dark:disabled:bg-slate-800/60',
        props.size === 'md' ? 'border-slate-300 dark:border-slate-700' : 'border-slate-200 dark:border-slate-700',
        props.rounded === 'lg' ? 'rounded-lg' : 'rounded-md',
        sizeCls[props.size],
      ]
    : 'disabled:cursor-not-allowed disabled:opacity-50',
  props.triggerClass,
])
const textCls = computed(() => {
  if (props.variant !== 'outline') return ''
  return selected.value ? 'text-slate-700 dark:text-slate-200' : 'text-slate-400 dark:text-slate-500'
})
const optionCls = computed(() => (props.size === 'md' ? 'px-3 py-2 text-sm' : 'px-3 py-1.5 text-xs'))
</script>

<template>
  <div ref="rootEl" class="relative">
    <button
      ref="triggerEl"
      type="button"
      :disabled="disabled"
      :class="triggerClasses"
      aria-haspopup="listbox"
      :aria-expanded="open"
      @click="open ? close() : openDropdown()"
      @keydown="onTriggerKey"
    >
      <span :class="['min-w-0 flex-1 truncate', textCls]">{{ displayText }}</span>
      <span class="flex shrink-0 items-center gap-0.5">
        <span
          v-if="clearable && selected && !disabled"
          class="rounded p-0.5 text-slate-400 hover:bg-slate-100 hover:text-slate-600 dark:hover:bg-slate-700 dark:hover:text-slate-300"
          :title="$t('common.select.clearSelection')"
          @click.stop="clear"
        >
          <X :class="iconCls[size]" />
        </span>
        <ChevronDown :class="[iconCls[size], 'shrink-0 transition-transform', variant === 'outline' ? 'text-slate-400' : 'opacity-60', open && 'rotate-180']" />
      </span>
    </button>

    <Teleport to="body">
      <div
        v-if="open"
        ref="panelEl"
        :style="panelStyle"
        class="z-[100] flex w-max flex-col overflow-hidden rounded-md border border-slate-200 bg-white shadow-lg dark:border-slate-700 dark:bg-slate-900"
      >
        <input
          v-if="searchable"
          ref="inputEl"
          v-model="query"
          type="text"
          :placeholder="searchPlaceholder ?? $t('common.select.searchPlaceholder')"
          :class="['w-full shrink-0 border-b border-slate-100 bg-transparent px-3 py-2 focus:outline-none dark:border-slate-800 dark:text-slate-100 dark:placeholder-slate-500', size === 'md' ? 'text-sm' : 'text-xs']"
          @keydown="onKey"
        />
        <ul ref="listEl" role="listbox" class="flex-1 overflow-y-auto py-1">
          <li v-if="filtered.length === 0" :class="[optionCls, 'text-slate-400 dark:text-slate-500']">{{ $t('common.select.noResults') }}</li>
          <li
            v-for="(o, i) in filtered"
            :key="String(keyOf(o))"
            role="option"
            :aria-selected="isSelected(o)"
            :data-active="i === highlighted"
          >
            <button
              type="button"
              tabindex="-1"
              :class="[
                'flex w-full items-center justify-between gap-2 text-left transition-colors',
                optionCls,
                i === highlighted ? 'bg-emerald-50 text-emerald-700 dark:bg-emerald-500/10 dark:text-emerald-400' : 'text-slate-700 hover:bg-slate-50 dark:text-slate-200 dark:hover:bg-slate-800/60',
                isSelected(o) ? 'font-medium' : '',
              ]"
              @mousedown.prevent="selectOption(o)"
              @mouseenter="highlighted = i"
            >
              <span class="truncate">{{ labelOf(o) }}</span>
              <Check v-if="isSelected(o)" :class="[iconCls[size], 'shrink-0 text-emerald-500 dark:text-emerald-400']" />
            </button>
          </li>
        </ul>
      </div>
    </Teleport>
  </div>
</template>
