<script setup lang="ts">
// 데모 모드일 때만 전역 상단에 표시되는 안내 띠.
const { enabled: demoEnabled } = useDemo()
</script>

<template>
  <div
    v-if="demoEnabled"
    class="flex items-center justify-center gap-2 bg-amber-400 px-4 py-1.5 text-center text-xs font-medium text-amber-950"
  >
    <span class="rounded bg-amber-950/15 px-1.5 py-0.5 text-[10px] font-bold uppercase tracking-wide">
      Demo
    </span>
    <span>데모 버전입니다 — 테스트용 환경이며 실제 데이터가 아닙니다.</span>
  </div>
</template>
