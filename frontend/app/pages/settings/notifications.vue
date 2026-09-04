<script setup lang="ts">
import { Bug, CheckCircle2, CornerDownRight, Info, Loader2, MessageSquare, MonitorSmartphone, Moon, Send } from '@lucide/vue'
import SettingsHeader from '~/components/base/SettingsHeader.vue'
import SettingRow from '~/components/base/SettingRow.vue'
import ToggleSwitch from '~/components/base/ToggleSwitch.vue'
import TeamsTestResultModal from '~/components/feature/TeamsTestResultModal.vue'
import type { TeamsTestResult } from '~/types/api'

/** 알림 설정 — 종류별 토글 · 방해금지 시간대 · MS Teams 연동을 한 화면에서 (하나의 저장 버튼) */
definePageMeta({ layout: 'settings' })

const auth = useAuthStore()
const membersApi = useMembers()
const { settings, quietEnabled, loading, saving, message, isError, load, save } = useNotificationSettings()
onMounted(load)

// 알림 종류 토글 메타 (백엔드 type: qa / comment / reply)
const typeItems = [
  { key: 'notifyQaEnabled', label: 'admin.settings.notifyQa', description: 'admin.settings.notifyQaDesc', icon: Bug },
  { key: 'notifyCommentEnabled', label: 'admin.settings.notifyComment', description: 'admin.settings.notifyCommentDesc', icon: MessageSquare },
  { key: 'notifyReplyEnabled', label: 'admin.settings.notifyReply', description: 'admin.settings.notifyReplyDesc', icon: CornerDownRight },
] as const

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

const timeCls = 'rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm focus:border-emerald-500 focus:outline-none focus:ring-2 focus:ring-emerald-500/20 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100'
const sectionCls = 'mb-2 text-[11px] font-semibold uppercase tracking-wider text-slate-400 dark:text-slate-500'
</script>

<template>
  <div>
    <SettingsHeader :title="$t('shell.settings.notifications.title')" :subtitle="$t('shell.settings.notifications.subtitle')" />

    <div v-if="loading" class="flex items-center gap-2 py-10 text-sm text-slate-400 dark:text-slate-500">
      <Loader2 class="h-4 w-4 animate-spin" /> {{ $t('admin.settings.loadingSettings') }}
    </div>
    <div v-else class="max-w-3xl space-y-7">
      <!-- MS Teams -->
      <section id="teams">
        <p :class="sectionCls">{{ $t('shell.settings.notifications.sectionTeams') }}</p>
        <div class="space-y-3">
          <SettingRow :icon="MonitorSmartphone" :title="$t('admin.settings.teamsMasterTitle')" :description="$t('admin.settings.teamsMasterDesc')">
            <template #control><ToggleSwitch v-model="settings.teamsNotifyEnabled" /></template>
          </SettingRow>
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
        </div>
      </section>

      <!-- 알림 종류 -->
      <section>
        <p :class="sectionCls">{{ $t('shell.settings.notifications.sectionTypes') }}</p>
        <i18n-t keypath="admin.settings.scopeNotice" scope="global" tag="p" class="mb-3 rounded-md bg-slate-50 px-3 py-2 text-xs text-slate-500 dark:bg-slate-800/50 dark:text-slate-400">
          <template #teams><span class="font-medium text-slate-600 dark:text-slate-300">{{ $t('admin.settings.scopeNoticeTeams') }}</span></template>
        </i18n-t>
        <div class="space-y-3">
          <SettingRow v-for="it in typeItems" :key="it.key" :icon="it.icon" :title="$t(it.label)" :description="$t(it.description)">
            <template #control><ToggleSwitch v-model="settings[it.key]" /></template>
          </SettingRow>
        </div>
      </section>

      <!-- 방해금지 -->
      <section>
        <p :class="sectionCls">{{ $t('shell.settings.notifications.sectionQuiet') }}</p>
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
      </section>

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
