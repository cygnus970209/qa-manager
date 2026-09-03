<script setup lang="ts">
import { Bell, Download, ExternalLink, Info, Loader2, Monitor, RefreshCw } from '@lucide/vue'
import SettingsHeader from '~/components/base/SettingsHeader.vue'
import SettingRow from '~/components/base/SettingRow.vue'
import type { DesktopInfo, DesktopNotificationPermission } from '~/composables/useDesktop'

definePageMeta({ layout: 'settings' })

const DOWNLOAD_URL = 'https://github.com/cygnus970209/qa-manager-desktop/releases/latest'

const desktop = useDesktop()
const info = ref<DesktopInfo | null>(null)
const permission = ref<DesktopNotificationPermission | null>(null)
const checking = ref(false)
const requesting = ref(false)

const platformLabel = computed(() => {
  const p = info.value?.platform
  return p === 'macos' ? 'macOS' : p === 'windows' ? 'Windows' : p === 'linux' ? 'Linux' : (p ?? '-')
})

async function refresh() {
  info.value = await desktop.getInfo()
  permission.value = await desktop.getNotificationPermission()
}
onMounted(refresh)
// 시스템 설정에서 권한을 바꾸고 돌아왔을 때 갱신
function onVisible() {
  if (document.visibilityState === 'visible') refresh()
}
onMounted(() => document.addEventListener('visibilitychange', onVisible))
onBeforeUnmount(() => document.removeEventListener('visibilitychange', onVisible))

async function checkUpdate() {
  checking.value = true
  try {
    await desktop.checkForUpdate()
  } finally {
    checking.value = false
  }
}
async function requestPermission() {
  requesting.value = true
  try {
    const r = await desktop.requestNotificationPermission()
    if (r) permission.value = r
  } finally {
    requesting.value = false
  }
}

const btnCls = 'inline-flex items-center gap-1.5 whitespace-nowrap rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-xs font-medium text-slate-700 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-200 dark:hover:bg-slate-800/60'
</script>

<template>
  <div>
    <SettingsHeader :title="$t('shell.settings.desktop.title')" :subtitle="$t('shell.settings.desktop.subtitle')" />

    <ClientOnly>
      <!-- 브라우저에서 보는 경우 -->
      <div v-if="!desktop.isDesktop.value" class="max-w-3xl space-y-4">
        <div class="flex items-start gap-3 rounded-xl border border-blue-100 bg-blue-50/60 p-4 text-sm text-slate-700 dark:border-blue-500/30 dark:bg-blue-500/10 dark:text-slate-200">
          <Info class="mt-0.5 h-4 w-4 shrink-0 text-blue-500 dark:text-blue-400" />
          <p>{{ $t('shell.settings.desktop.notDesktop') }}</p>
        </div>
        <a
          :href="DOWNLOAD_URL"
          target="_blank"
          rel="noopener noreferrer"
          class="inline-flex items-center gap-1.5 rounded-lg bg-slate-800 px-4 py-2 text-sm font-medium text-white hover:bg-slate-900 dark:bg-slate-700 dark:hover:bg-slate-600"
        >
          <Download class="h-4 w-4" /> {{ $t('shell.settings.desktop.download') }} <ExternalLink class="h-3.5 w-3.5 opacity-70" />
        </a>
      </div>

      <div v-else class="max-w-3xl space-y-3">
        <!-- 버전 -->
        <SettingRow :icon="Monitor" :title="$t('shell.settings.desktop.version')" :description="info ? `${platformLabel} · v${info.version}` : $t('shell.settings.desktop.unavailable')" />

        <!-- 업데이트 -->
        <SettingRow :icon="RefreshCw" :title="$t('shell.settings.desktop.update')" :description="$t('shell.settings.desktop.updateDesc')">
          <template #control>
            <button type="button" :class="btnCls" :disabled="checking || !desktop.supports('checkForUpdate')" @click="checkUpdate">
              <Loader2 v-if="checking" class="h-3.5 w-3.5 animate-spin" />
              {{ checking ? $t('shell.settings.desktop.checking') : $t('shell.settings.desktop.checkUpdate') }}
            </button>
          </template>
          <p v-if="!desktop.supports('checkForUpdate')" class="mt-2 pl-[52px] text-xs text-slate-400 dark:text-slate-500">{{ $t('shell.settings.desktop.unavailable') }}</p>
        </SettingRow>

        <!-- 알림 권한 -->
        <SettingRow :icon="Bell" :title="$t('shell.settings.desktop.notificationPermission')" :description="$t('shell.settings.desktop.permissionDesc')">
          <template #control>
            <span
              v-if="permission"
              :class="[
                'inline-flex items-center whitespace-nowrap rounded-full px-2.5 py-0.5 text-xs font-medium',
                permission === 'granted' ? 'bg-emerald-50 text-emerald-600 dark:bg-emerald-500/10 dark:text-emerald-400'
                  : permission === 'denied' ? 'bg-rose-50 text-rose-600 dark:bg-rose-500/10 dark:text-rose-400'
                    : 'bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-300',
              ]"
            >{{ $t(`shell.settings.desktop.permission.${permission}`) }}</span>
            <button v-if="permission === 'not_determined'" type="button" :class="btnCls" :disabled="requesting" @click="requestPermission">
              <Loader2 v-if="requesting" class="h-3.5 w-3.5 animate-spin" />
              {{ $t('shell.settings.desktop.requestPermission') }}
            </button>
            <button v-else-if="permission === 'denied'" type="button" :class="btnCls" @click="desktop.openNotificationSettings()">
              {{ $t('shell.settings.desktop.openSystemSettings') }}
            </button>
          </template>
          <p v-if="!desktop.supports('getNotificationPermission')" class="mt-2 pl-[52px] text-xs text-slate-400 dark:text-slate-500">{{ $t('shell.settings.desktop.unavailable') }}</p>
        </SettingRow>
      </div>
    </ClientOnly>
  </div>
</template>
