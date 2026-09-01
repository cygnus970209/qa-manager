<script setup lang="ts">
import SettingsPanel from '~/components/feature/SettingsPanel.vue'
</script>

<template>
  <section>
    <h1 class="text-2xl font-bold tracking-tight text-slate-900 dark:text-slate-100">{{ $t('shell.settings.title') }}</h1>
    <p class="mt-1 text-sm text-slate-500 dark:text-slate-400">{{ $t('shell.settings.subtitle') }}</p>

    <div class="mt-6 rounded-xl border border-slate-200 bg-white p-5 dark:border-slate-800 dark:bg-slate-900">
      <SettingsPanel scope="personal" />
    </div>
  </section>
</template>
