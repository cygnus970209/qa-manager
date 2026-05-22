<script setup lang="ts">
import AppNavbar from '~/components/feature/AppNavbar.vue'
</script>

<template>
  <div class="min-h-screen bg-gray-50 text-gray-900">
    <AppNavbar />
    <main class="mx-auto max-w-7xl px-4 py-6">
      <slot />
    </main>
  </div>
</template>
