<script setup lang="ts">
import { Bug, CheckCircle2, CornerDownRight, Loader2, MessageSquare, Moon } from '@lucide/vue'
import SettingsHeader from '~/components/base/SettingsHeader.vue'
import SettingRow from '~/components/base/SettingRow.vue'
import ToggleSwitch from '~/components/base/ToggleSwitch.vue'

definePageMeta({ layout: 'settings' })

const { settings, quietEnabled, loading, saving, message, isError, load, save } = useNotificationSettings()
onMounted(load)

// 알림 종류 토글 메타 (백엔드 type: qa / comment / reply)
const typeItems = [
  { key: 'notifyQaEnabled', label: 'admin.settings.notifyQa', description: 'admin.settings.notifyQaDesc', icon: Bug },
  { key: 'notifyCommentEnabled', label: 'admin.settings.notifyComment', description: 'admin.settings.notifyCommentDesc', icon: MessageSquare },
  { key: 'notifyReplyEnabled', label: 'admin.settings.notifyReply', description: 'admin.settings.notifyReplyDesc', icon: CornerDownRight },
] as const

const timeCls = 'rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm focus:border-emerald-500 focus:outline-none focus:ring-2 focus:ring-emerald-500/20 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100'
</script>

<template>
  <div>
    <SettingsHeader :title="$t('shell.settings.notifications.title')" :subtitle="$t('shell.settings.notifications.subtitle')" />

    <div v-if="loading" class="flex items-center gap-2 py-10 text-sm text-slate-400 dark:text-slate-500">
      <Loader2 class="h-4 w-4 animate-spin" /> {{ $t('admin.settings.loadingSettings') }}
    </div>
    <div v-else class="max-w-3xl space-y-5">
      <i18n-t keypath="admin.settings.scopeNotice" scope="global" tag="p" class="rounded-md bg-slate-50 px-3 py-2 text-xs text-slate-500 dark:bg-slate-800/50 dark:text-slate-400">
        <template #teams><span class="font-medium text-slate-600 dark:text-slate-300">{{ $t('admin.settings.scopeNoticeTeams') }}</span></template>
      </i18n-t>

      <div class="space-y-3">
        <SettingRow v-for="it in typeItems" :key="it.key" :icon="it.icon" :title="$t(it.label)" :description="$t(it.description)">
          <template #control><ToggleSwitch v-model="settings[it.key]" /></template>
        </SettingRow>
      </div>

      <SettingRow :icon="Moon" :title="$t('admin.settings.quietHoursTitle')" :description="$t('admin.settings.quietHoursDesc')">
        <template #control><ToggleSwitch v-model="quietEnabled" /></template>
        <div v-if="quietEnabled" class="mt-4 flex items-center gap-3 pl-[52px]">
          <div>
            <label class="mb-1 block text-xs font-medium text-slate-600 dark:text-slate-300">{{ $t('admin.settings.quietStart') }}</label>
            <input v-model="settings.quietHoursStart" type="time" :class="timeCls" />
          </div>
          <span class="mt-5 text-slate-400 dark:text-slate-500">~</span>
          <div>
            <label class="mb-1 block text-xs font-medium text-slate-600 dark:text-slate-300">{{ $t('admin.settings.quietEnd') }}</label>
            <input v-model="settings.quietHoursEnd" type="time" :class="timeCls" />
          </div>
        </div>
        <p v-if="quietEnabled" class="mt-2 pl-[52px] text-xs text-slate-400 dark:text-slate-500">{{ $t('admin.settings.quietHoursHint') }}</p>
      </SettingRow>

      <div class="flex items-center gap-3 border-t border-slate-100 pt-4 dark:border-slate-800">
        <button
          type="button"
          :disabled="saving"
          class="inline-flex items-center gap-1.5 rounded-lg bg-emerald-600 px-5 py-2 text-sm font-medium text-white transition-colors hover:bg-emerald-700 disabled:cursor-not-allowed disabled:opacity-60"
          @click="save"
        >
          <Loader2 v-if="saving" class="h-4 w-4 animate-spin" />
          <CheckCircle2 v-else class="h-4 w-4" />
          {{ $t('common.actions.save') }}
        </button>
        <span v-if="message" :class="['text-xs', isError ? 'text-rose-500 dark:text-rose-400' : 'text-emerald-600 dark:text-emerald-400']">{{ message }}</span>
      </div>
    </div>
  </div>
</template>
