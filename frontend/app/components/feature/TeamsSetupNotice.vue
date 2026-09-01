<script setup lang="ts">
import { BellRing, AlertTriangle } from '@lucide/vue'
import AppDialog from '~/components/base/AppDialog.vue'

defineProps<{ open: boolean }>()
const emit = defineEmits<{ close: []; confirm: [] }>()
</script>

<template>
  <AppDialog :open="open" :title="$t('admin.teamsNotice.title')" @close="emit('close')">
    <div class="space-y-4">
      <div class="flex items-start gap-2.5 rounded-lg bg-rose-50 px-3.5 py-3 dark:bg-rose-500/10">
        <AlertTriangle class="mt-0.5 h-5 w-5 shrink-0 text-rose-500 dark:text-rose-400" />
        <p class="text-sm text-rose-700 dark:text-rose-300">
          <span class="font-semibold">{{ $t('admin.teamsNotice.warnTitle') }}</span><br />
          {{ $t('admin.teamsNotice.warnBody') }}
          <span class="font-semibold">{{ $t('admin.teamsNotice.warnEmphasis') }}</span>
        </p>
      </div>

      <i18n-t keypath="admin.teamsNotice.body" scope="global" tag="p" class="text-sm leading-relaxed text-slate-600 dark:text-slate-300">
        <template #bot><span class="font-medium text-slate-800 dark:text-slate-100">{{ $t('admin.teamsNotice.botName') }}</span></template>
      </i18n-t>

      <img
        src="/teams_app.jpg"
        :alt="$t('admin.teamsNotice.imageAlt')"
        class="w-full rounded-lg border border-slate-200 dark:border-slate-800"
      />
    </div>

    <template #footer>
      <button
        type="button"
        class="rounded-md px-3 py-1.5 text-xs font-medium text-slate-500 hover:bg-slate-100 dark:text-slate-400 dark:hover:bg-slate-800"
        @click="emit('close')"
      >
        {{ $t('admin.teamsNotice.later') }}
      </button>
      <button
        type="button"
        class="inline-flex items-center gap-1.5 rounded-md bg-emerald-600 px-4 py-1.5 text-xs font-semibold text-white hover:bg-emerald-700"
        @click="emit('confirm')"
      >
        <BellRing class="h-4 w-4" />
        {{ $t('admin.teamsNotice.goToSettings') }}
      </button>
    </template>
  </AppDialog>
</template>
