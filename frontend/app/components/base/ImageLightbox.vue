<script setup lang="ts">
import { Minus, Plus, RotateCcw, X } from '@lucide/vue'

const props = defineProps<{ src: string | null }>()
const emit = defineEmits<{ close: [] }>()

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

function reset() {
  scale.value = 1
  tx.value = 0
  ty.value = 0
}

watch(() => props.src, (v) => {
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
  if (!props.src) return
  if (e.key === 'Escape') emit('close')
  else if (e.key === '+' || e.key === '=') zoomIn()
  else if (e.key === '-' || e.key === '_') zoomOut()
  else if (e.key === '0') reset()
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
      v-if="src"
      class="fixed inset-0 z-[60] flex items-center justify-center bg-black/80 p-4 select-none"
      @mousedown="onBackdrop"
      @wheel.passive="onWheel"
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
      </div>

      <button
        type="button"
        class="absolute right-4 top-4 z-10 rounded-full bg-white/10 p-2 text-white hover:bg-white/20"
        aria-label="닫기"
        @click.stop="emit('close')"
      >
        <X class="h-5 w-5" />
      </button>

      <img
        :src="src"
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
        휠로 확대/축소 · 드래그로 이동 · ESC 닫기 · 0 키 리셋
      </p>
    </div>
  </Teleport>
</template>
