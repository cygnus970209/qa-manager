<script setup lang="ts">
import { CheckCircle2, Info, Loader2, MonitorSmartphone, Send } from '@lucide/vue'
import SettingsHeader from '~/components/base/SettingsHeader.vue'
import SettingRow from '~/components/base/SettingRow.vue'
import ToggleSwitch from '~/components/base/ToggleSwitch.vue'
import TeamsTestResultModal from '~/components/feature/TeamsTestResultModal.vue'
import type { TeamsTestResult } from '~/types/api'

definePageMeta({ layout: 'settings' })

const auth = useAuthStore()
const membersApi = useMembers()
const { settings, loading, saving, message, isError, load, save } = useNotificationSettings()
onMounted(load)

/* ─── Teams 테스트 발송 ─── */
const testOpen = ref(false)
const testLoading = ref(false)
const testResult = ref<TeamsTestResult | null>(null)
async function sendTest() {
  if (!auth.user) return
  testOpen.value = true
  testLoading.value = true
  testResult.value = null
  try {
    testResult.value = await membersApi.teamsTest(auth.user.id)
  } finally {
    testLoading.value = false
  }
}
</script>

<template>
  <div>
    <SettingsHeader :title="$t('shell.settings.teams.title')" :subtitle="$t('shell.settings.teams.subtitle')" />

    <div v-if="loading" class="flex items-center gap-2 py-10 text-sm text-slate-400 dark:text-slate-500">
      <Loader2 class="h-4 w-4 animate-spin" /> {{ $t('admin.settings.loadingSettings') }}
    </div>
    <div v-else class="max-w-xl space-y-5">
      <SettingRow :icon="MonitorSmartphone" :title="$t('admin.settings.teamsMasterTitle')" :description="$t('admin.settings.teamsMasterDesc')">
        <template #control><ToggleSwitch v-model="settings.teamsNotifyEnabled" /></template>
      </SettingRow>

      <!-- 봇 설치 안내 -->
      <div class="rounded-xl border border-blue-100 bg-blue-50/60 p-4 dark:border-blue-500/30 dark:bg-blue-500/10">
        <div class="flex items-start gap-3">
          <Info class="mt-0.5 h-4 w-4 shrink-0 text-blue-500 dark:text-blue-400" />
          <div class="space-y-2 text-sm text-slate-700 dark:text-slate-200">
            <p class="font-semibold text-slate-800 dark:text-slate-100">{{ $t('admin.settings.botInstallTitle') }}</p>
            <ol class="list-decimal space-y-1 pl-4 text-xs text-slate-600 dark:text-slate-300">
              <i18n-t keypath="admin.settings.botStep1" scope="global" tag="li">
                <template #apps><span class="font-medium">{{ $t('admin.settings.botStep1Apps') }}</span></template>
                <template #manageApps><span class="font-medium">{{ $t('admin.settings.botStep1ManageApps') }}</span></template>
                <template #uploadApp><span class="font-medium">{{ $t('admin.settings.botStep1UploadApp') }}</span></template>
              </i18n-t>
              <li>{{ $t('admin.settings.botStep2') }}</li>
              <i18n-t keypath="admin.settings.botStep3" scope="global" tag="li">
                <template #testSend><span class="font-medium">{{ $t('admin.settings.botStep3TestSend') }}</span></template>
              </i18n-t>
            </ol>
            <p class="text-xs text-slate-400 dark:text-slate-500">{{ $t('admin.settings.botInstallFootnote') }}</p>
          </div>
        </div>
      </div>

      <!-- 테스트 발송 -->
      <div class="flex items-center gap-3">
        <button
          type="button"
          :disabled="testLoading || !settings.teamsNotifyEnabled"
          class="inline-flex items-center gap-1.5 whitespace-nowrap rounded-lg bg-emerald-500 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-emerald-600 disabled:cursor-not-allowed disabled:opacity-60"
          @click="sendTest"
        >
          <Loader2 v-if="testLoading" class="h-4 w-4 animate-spin" />
          <Send v-else class="h-4 w-4" />
          {{ $t('admin.settings.sendTestToMe') }}
        </button>
        <span v-if="!settings.teamsNotifyEnabled" class="text-xs text-slate-400 dark:text-slate-500">{{ $t('admin.settings.testRequiresEnabled') }}</span>
      </div>

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

    <TeamsTestResultModal
      :open="testOpen"
      :loading="testLoading"
      :result="testResult"
      :member-name="auth.user?.name"
      @close="testOpen = false"
    />
  </div>
</template>
