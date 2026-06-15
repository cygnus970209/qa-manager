<script setup lang="ts">
import { ChevronLeft, ChevronRight, Minus, Plus, RotateCcw, X } from '@lucide/vue'

const props = defineProps<{
  /** 라이트박스에서 함께 탐색 가능한 이미지 묶음. */
  images?: string[]
  /** 현재 표시할 이미지 인덱스. null 이면 닫힘. */
  index?: number | null
  /** 하위 호환: 단일 이미지만 표시하고 싶을 때. images 와 동시 사용 금지. */
  src?: string | null
}>()
const emit = defineEmits<{
  close: []
  'update:index': [value: number]
}>()

const MIN = 0.25
const MAX = 8
const STEP = 0.25

const scale = ref(1)
const tx = ref(0)
const ty = ref(0)
const dragging = ref(false)
const startX = ref(0)
const startY = ref(0)
const startTx = ref(0)
const startTy = ref(0)

/** images / src 둘 다 지원해서 호환. */
const list = computed<string[]>(() => {
  if (props.images && props.images.length > 0) return props.images
  if (props.src) return [props.src]
  return []
})

const currentIndex = computed<number>(() => {
  if (props.index != null) return props.index
  return list.value.length > 0 ? 0 : -1
})

const isOpen = computed<boolean>(() => {
  if (props.src !== undefined) return props.src !== null
  return props.index !== undefined && props.index !== null && list.value.length > 0
})

const currentSrc = computed<string | null>(() => {
  if (!isOpen.value) return null
  const i = currentIndex.value
  if (i < 0 || i >= list.value.length) return null
  return list.value[i] ?? null
})

const hasPrev = computed(() => list.value.length > 1 && currentIndex.value > 0)
const hasNext = computed(() => list.value.length > 1 && currentIndex.value < list.value.length - 1)

function reset() {
  scale.value = 1
  tx.value = 0
  ty.value = 0
}

watch(currentSrc, (v) => {
  if (v) reset()
})

function clampScale(v: number) {
  return Math.min(MAX, Math.max(MIN, v))
}

function zoomIn() { scale.value = clampScale(scale.value + STEP) }
function zoomOut() {
  const next = clampScale(scale.value - STEP)
  scale.value = next
  if (next === 1) { tx.value = 0; ty.value = 0 }
}

function goPrev() {
  if (!hasPrev.value) return
  emit('update:index', currentIndex.value - 1)
}
function goNext() {
  if (!hasNext.value) return
  emit('update:index', currentIndex.value + 1)
}

function onWheel(e: WheelEvent) {
  e.preventDefault()
  const delta = e.deltaY > 0 ? -STEP : STEP
  scale.value = clampScale(scale.value + delta)
  if (scale.value === 1) { tx.value = 0; ty.value = 0 }
}

function onMouseDown(e: MouseEvent) {
  if (scale.value <= 1) return
  dragging.value = true
  startX.value = e.clientX
  startY.value = e.clientY
  startTx.value = tx.value
  startTy.value = ty.value
  e.preventDefault()
}
function onMouseMove(e: MouseEvent) {
  if (!dragging.value) return
  tx.value = startTx.value + (e.clientX - startX.value)
  ty.value = startTy.value + (e.clientY - startY.value)
}
function onMouseUp() { dragging.value = false }

function onKey(e: KeyboardEvent) {
  if (!isOpen.value) return
  if (e.key === 'Escape') emit('close')
  else if (e.key === '+' || e.key === '=') zoomIn()
  else if (e.key === '-' || e.key === '_') zoomOut()
  else if (e.key === '0') reset()
  else if (e.key === 'ArrowLeft') goPrev()
  else if (e.key === 'ArrowRight') goNext()
}

onMounted(() => {
  document.addEventListener('keydown', onKey)
  document.addEventListener('mousemove', onMouseMove)
  document.addEventListener('mouseup', onMouseUp)
})
onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKey)
  document.removeEventListener('mousemove', onMouseMove)
  document.removeEventListener('mouseup', onMouseUp)
})

function onBackdrop(e: MouseEvent) {
  if (e.target === e.currentTarget) emit('close')
}
</script>

<template>
  <Teleport to="body">
    <div
      v-if="isOpen && currentSrc"
      class="fixed inset-0 z-[60] flex items-center justify-center bg-black/80 p-4 select-none"
      @mousedown="onBackdrop"
      @wheel="onWheel"
    >
      <!-- 상단 도구막대 -->
      <div class="absolute top-3 left-1/2 z-10 flex -translate-x-1/2 items-center gap-1 rounded-full bg-white/10 px-2 py-1 backdrop-blur">
        <button
          type="button"
          class="rounded-full p-1.5 text-white hover:bg-white/20 disabled:opacity-40"
          :disabled="scale <= MIN"
          aria-label="축소"
          @click.stop="zoomOut"
        >
          <Minus class="h-4 w-4" />
        </button>
        <span class="min-w-[3.5rem] text-center text-xs text-white tabular-nums">{{ Math.round(scale * 100) }}%</span>
        <button
          type="button"
          class="rounded-full p-1.5 text-white hover:bg-white/20 disabled:opacity-40"
          :disabled="scale >= MAX"
          aria-label="확대"
          @click.stop="zoomIn"
        >
          <Plus class="h-4 w-4" />
        </button>
        <span class="mx-1 h-4 w-px bg-white/20" />
        <button
          type="button"
          class="rounded-full p-1.5 text-white hover:bg-white/20"
          aria-label="원래 크기"
          @click.stop="reset"
        >
          <RotateCcw class="h-4 w-4" />
        </button>
        <span v-if="list.length > 1" class="mx-1 h-4 w-px bg-white/20" />
        <span v-if="list.length > 1" class="px-1 text-xs text-white tabular-nums">
          {{ currentIndex + 1 }} / {{ list.length }}
        </span>
      </div>

      <button
        type="button"
        class="absolute right-4 top-4 z-10 rounded-full bg-white/10 p-2 text-white hover:bg-white/20"
        aria-label="닫기"
        @click.stop="emit('close')"
      >
        <X class="h-5 w-5" />
      </button>

      <!-- 좌/우 네비 -->
      <button
        v-if="hasPrev"
        type="button"
        class="absolute left-4 top-1/2 z-10 -translate-y-1/2 rounded-full bg-white/10 p-2 text-white hover:bg-white/20"
        aria-label="이전 이미지"
        @click.stop="goPrev"
      >
        <ChevronLeft class="h-6 w-6" />
      </button>
      <button
        v-if="hasNext"
        type="button"
        class="absolute right-4 top-1/2 z-10 -translate-y-1/2 rounded-full bg-white/10 p-2 text-white hover:bg-white/20"
        aria-label="다음 이미지"
        @click.stop="goNext"
      >
        <ChevronRight class="h-6 w-6" />
      </button>

      <img
        :src="currentSrc"
        alt="확대 이미지"
        :style="{
          transform: `translate(${tx}px, ${ty}px) scale(${scale})`,
          cursor: scale > 1 ? (dragging ? 'grabbing' : 'grab') : 'default',
          transition: dragging ? 'none' : 'transform 0.12s ease-out',
        }"
        class="max-h-[85vh] max-w-[90vw] rounded-md object-contain"
        draggable="false"
        @mousedown.stop="onMouseDown"
        @click.stop
      />

      <p class="absolute bottom-3 left-1/2 -translate-x-1/2 text-[11px] text-white/60">
        휠로 확대/축소 · 드래그로 이동 · ←/→ 이전/다음 · ESC 닫기 · 0 키 리셋
      </p>
    </div>
  </Teleport>
</template>
