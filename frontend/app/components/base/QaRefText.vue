<script setup lang="ts">
import type { Member } from '~/types/api'

/**
 * 본문 텍스트를 분해해 `#123` 형태의 QA 참조는 상세 페이지 링크로,
 * `@이름` 멘션(members 전달 시, 실제 멤버명과 일치할 때만)은 하이라이트로 렌더링한다.
 */
const props = defineProps<{
  text: string
  members?: Member[]
}>()

type Segment = { text: string; kind: 'plain' | 'mention' | 'qaref'; qaId?: number }

const segments = computed<Segment[]>(() => {
  const parts = props.text.split(/(@[^\s#]+|#\d+)/g)
  return parts.filter((p) => p !== '').map((p): Segment => {
    if (/^#\d+$/.test(p)) {
      return { text: p, kind: 'qaref', qaId: Number(p.slice(1)) }
    }
    if (p.startsWith('@') && (props.members ?? []).some((m) => m.name === p.slice(1))) {
      return { text: p, kind: 'mention' }
    }
    return { text: p, kind: 'plain' }
  })
})
</script>

<template>
  <template v-for="(seg, i) in segments" :key="i">
    <NuxtLink
      v-if="seg.kind === 'qaref'"
      :to="`/qa/${seg.qaId}`"
      class="rounded bg-blue-50 px-0.5 font-medium text-blue-600 hover:underline"
      @click.stop
    >{{ seg.text }}</NuxtLink>
    <span
      v-else
      :class="seg.kind === 'mention' ? 'rounded bg-emerald-50 px-0.5 font-medium text-emerald-600' : ''"
    >{{ seg.text }}</span>
  </template>
</template>
