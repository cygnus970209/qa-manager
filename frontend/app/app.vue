<script setup lang="ts">
import DemoBanner from '~/components/feature/DemoBanner.vue'
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
