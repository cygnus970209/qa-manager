<script setup lang="ts">
import { Check } from '@lucide/vue'
import SettingsHeader from '~/components/base/SettingsHeader.vue'

definePageMeta({ layout: 'settings' })

// 테마 (vueuse 의 useColorMode 와 이름이 겹쳐 모듈 주입 $colorMode 를 직접 사용)
const colorMode = useNuxtApp().$colorMode
const options = [
  { value: 'light', labelKey: 'common.theme.light' },
  { value: 'dark', labelKey: 'common.theme.dark' },
  { value: 'system', labelKey: 'common.theme.system' },
] as const
function select(v: 'light' | 'dark' | 'system') {
  colorMode.preference = v
}
</script>

<template>
  <div>
    <SettingsHeader :title="$t('shell.settings.appearance.title')" :subtitle="$t('shell.settings.appearance.subtitle')" />

    <p class="mb-2.5 text-[11px] font-semibold uppercase tracking-wider text-slate-400 dark:text-slate-500">{{ $t('shell.settings.appearance.theme') }}</p>
    <ClientOnly>
      <div class="grid max-w-2xl grid-cols-1 gap-3 sm:grid-cols-3">
        <button
          v-for="opt in options"
          :key="opt.value"
          type="button"
          :class="[
            'rounded-xl border bg-white p-3 text-left transition dark:bg-slate-900',
            colorMode.preference === opt.value
              ? 'border-emerald-300 ring-2 ring-emerald-100 dark:border-emerald-500/50 dark:ring-emerald-500/20'
              : 'border-slate-200 hover:border-slate-300 dark:border-slate-800 dark:hover:border-slate-700',
          ]"
          @click="select(opt.value)"
        >
          <!-- 미리보기 -->
          <div class="flex h-24 overflow-hidden rounded-lg border border-slate-200 dark:border-slate-700">
            <div v-if="opt.value === 'light'" class="flex flex-1 bg-gray-50">
              <div class="w-[28%] border-r border-slate-200 bg-white" />
              <div class="flex flex-1 flex-col gap-1.5 p-2.5"><div class="h-2 w-3/5 rounded bg-slate-200" /><div class="flex-1 rounded-md border border-slate-200 bg-white" /></div>
            </div>
            <div v-else-if="opt.value === 'dark'" class="flex flex-1 bg-[#0b1120]">
              <div class="w-[28%] border-r border-slate-800 bg-slate-900" />
              <div class="flex flex-1 flex-col gap-1.5 p-2.5"><div class="h-2 w-3/5 rounded bg-slate-800" /><div class="flex-1 rounded-md border border-slate-800 bg-slate-900" /></div>
            </div>
            <template v-else>
              <div class="flex flex-1 bg-gray-50">
                <div class="w-[28%] border-r border-slate-200 bg-white" />
                <div class="flex flex-1 flex-col gap-1.5 p-2"><div class="h-2 w-3/5 rounded bg-slate-200" /><div class="flex-1 rounded-md border border-slate-200 bg-white" /></div>
              </div>
              <div class="flex flex-1 bg-[#0b1120]">
                <div class="w-[28%] border-r border-slate-800 bg-slate-900" />
                <div class="flex flex-1 flex-col gap-1.5 p-2"><div class="h-2 w-3/5 rounded bg-slate-800" /><div class="flex-1 rounded-md border border-slate-800 bg-slate-900" /></div>
              </div>
            </template>
          </div>
          <div class="mt-2.5 flex items-center gap-2">
            <span
              :class="[
                'flex h-4 w-4 items-center justify-center rounded-full border',
                colorMode.preference === opt.value ? 'border-emerald-500 bg-emerald-500 text-white' : 'border-slate-300 dark:border-slate-600',
              ]"
            ><Check v-if="colorMode.preference === opt.value" class="h-3 w-3" /></span>
            <span class="text-sm font-medium text-slate-800 dark:text-slate-100">{{ $t(opt.labelKey) }}</span>
          </div>
        </button>
      </div>
    </ClientOnly>
  </div>
</template>
