<script setup lang="ts">
/**
 * 줄바꿈(\n)을 보존하면서 지정 줄 수로 클램프하고,
 * 내용이 넘칠 때만 '더보기/접기' 토글을 노출하는 텍스트 표시 컴포넌트.
 */
const props = withDefaults(defineProps<{
  text?: string | null
  /** 접힌 상태에서 보여줄 줄 수 (기본 3). Tailwind 정적 클래스 매핑상 1~6 지원. */
  lines?: number
}>(), { lines: 3 })

const el = ref<HTMLParagraphElement | null>(null)
const expanded = ref(false)
const overflowing = ref(false)

// Tailwind purge 대응: 동적 문자열이 아닌 정적 클래스 맵.
const clampClass = computed(() => {
  switch (props.lines) {
    case 1: return 'line-clamp-1'
    case 2: return 'line-clamp-2'
    case 4: return 'line-clamp-4'
    case 5: return 'line-clamp-5'
    case 6: return 'line-clamp-6'
    default: return 'line-clamp-3'
  }
})

function measure() {
  const node = el.value
  if (!node || expanded.value) return // 펼친 상태에선 측정 무의미(scrollHeight==clientHeight)
  overflowing.value = node.scrollHeight - node.clientHeight > 1
}

onMounted(() => {
  nextTick(measure)
  if (typeof ResizeObserver !== 'undefined' && el.value) {
    const ro = new ResizeObserver(() => measure())
    ro.observe(el.value)
    onBeforeUnmount(() => ro.disconnect())
  }
})

// 텍스트가 바뀌면 접은 상태로 리셋 후 재측정.
watch(() => props.text, () => {
  expanded.value = false
  nextTick(measure)
})
</script>

<template>
  <div>
    <p
      ref="el"
      class="whitespace-pre-wrap text-sm text-slate-500"
      :class="expanded ? '' : clampClass"
    >{{ text }}</p>
    <button
      v-if="overflowing || expanded"
      type="button"
      class="mt-1 text-xs font-medium text-emerald-600 hover:text-emerald-700"
      @click="expanded = !expanded"
    >
      {{ expanded ? $t('qa.expandable.less') : $t('qa.expandable.more') }}
    </button>
  </div>
</template>
