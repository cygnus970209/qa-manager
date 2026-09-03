<script setup lang="ts">
import { Check } from '@lucide/vue'
import SettingsHeader from '~/components/base/SettingsHeader.vue'

definePageMeta({ layout: 'settings' })

const { locale, locales, setLocale } = useI18n()
async function select(code: 'ko' | 'en') {
  if (code !== locale.value) await setLocale(code)
}
</script>

<template>
  <div>
    <SettingsHeader :title="$t('shell.settings.language.title')" :subtitle="$t('shell.settings.language.subtitle')" />
    <div class="max-w-md overflow-hidden rounded-xl border border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-900">
      <button
        v-for="l in locales"
        :key="l.code"
        type="button"
        class="flex w-full items-center justify-between border-b border-slate-100 px-4 py-3 text-left text-sm last:border-0 hover:bg-slate-50 dark:border-slate-800 dark:hover:bg-slate-800/60"
        @click="select(l.code as 'ko' | 'en')"
      >
        <span :class="l.code === locale ? 'font-medium text-slate-800 dark:text-slate-100' : 'text-slate-700 dark:text-slate-200'">{{ l.name }}</span>
        <Check v-if="l.code === locale" class="h-4 w-4 text-emerald-500 dark:text-emerald-400" />
      </button>
    </div>
  </div>
</template>
