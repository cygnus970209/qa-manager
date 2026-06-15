<script setup lang="ts">
// 데모 모드일 때만 전역 상단에 표시되는 안내 띠 + 초기화 버튼.
const { enabled: demoEnabled, reset } = useDemo()
</script>

<template>
  <div
    v-if="demoEnabled"
    class="flex items-center justify-center gap-2 bg-amber-400 px-4 py-1.5 text-center text-xs font-medium text-amber-950"
  >
    <span class="rounded bg-amber-950/15 px-1.5 py-0.5 text-[10px] font-bold uppercase tracking-wide">
      Demo
    </span>
    <span>데모 버전입니다 — 변경 사항은 이 브라우저에만 저장되며 서버에 반영되지 않습니다.</span>
    <button
      type="button"
      class="ml-1 rounded bg-amber-950/15 px-2 py-0.5 font-semibold hover:bg-amber-950/25"
      @click="reset"
    >
      초기화
    </button>
  </div>
</template>
