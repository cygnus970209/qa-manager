<script setup lang="ts">
import DemoBanner from '~/components/feature/DemoBanner.vue'

// <html lang> 을 현재 언어에 맞게 동기화 (nuxt.config 의 정적 'ko' 를 덮어씀)
const localeHead = useLocaleHead()
useHead(() => ({ htmlAttrs: { lang: localeHead.value.htmlAttrs?.lang ?? 'ko' } }))
</script>

<template>
  <div>
    <NuxtRouteAnnouncer />
    <DemoBanner />
    <NuxtLayout>
      <NuxtPage />
    </NuxtLayout>
  </div>
</template>
