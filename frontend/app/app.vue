<script setup lang="ts">
import AppDialogHost from '~/components/base/AppDialogHost.vue'
import DemoBanner from '~/components/feature/DemoBanner.vue'
import AppUpdateBanner from '~/components/feature/AppUpdateBanner.vue'

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
    <AppDialogHost />
    <ClientOnly><AppUpdateBanner /></ClientOnly>
  </div>
</template>
