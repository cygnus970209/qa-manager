<script setup lang="ts">
import type { Member } from '~/types/api'

/**
 * 본문 텍스트를 분해해 `#123` 형태의 QA 참조는 상세 페이지 링크로,
 * `@이름` 멘션(members 전달 시, 실제 멤버명과 일치할 때만)은 하이라이트로 렌더링한다.
 *
 * QA 링크는 브라우저에선 새 탭(작성 중인 내용 유지), 데스크톱 앱에선 같은 창에서 이동한다 —
 * 데스크톱 셸(웹뷰)은 새 창 요청(target=_blank)을 처리하지 않아 클릭이 무시되기 때문.
 */
const props = defineProps<{
  text: string
  members?: Member[]
}>()

const desktop = useDesktop()
// SSR 에선 브리지가 안 보이므로 마운트 뒤에 판단해 target 속성이 실제 DOM 에 반영되게 한다.
const inDesktop = ref(false)
onMounted(() => { inDesktop.value = desktop.isDesktop.value })

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
      :target="inDesktop ? undefined : '_blank'"
      :rel="inDesktop ? undefined : 'noopener noreferrer'"
      class="rounded bg-blue-50 px-0.5 font-medium text-blue-600 hover:underline dark:bg-blue-500/10 dark:text-blue-400"
      @click.stop
    >{{ seg.text }}</NuxtLink>
    <span
      v-else
      :class="seg.kind === 'mention' ? 'rounded bg-emerald-50 px-0.5 font-medium text-emerald-600 dark:bg-emerald-500/10 dark:text-emerald-400' : ''"
    >{{ seg.text }}</span>
  </template>
</template>
