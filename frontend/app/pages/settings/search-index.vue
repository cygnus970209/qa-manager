<script setup lang="ts">
import { DatabaseZap, Loader2, RefreshCw } from '@lucide/vue'
import SettingsHeader from '~/components/base/SettingsHeader.vue'
import SettingRow from '~/components/base/SettingRow.vue'
import type { SearchStats, SearchType } from '~/types/api'

definePageMeta({ layout: 'settings' })

// 관리자 전용 — 일반 멤버는 내 계정으로 돌려보낸다 (API 는 백엔드 403 으로 별도 보호됨)
const auth = useAuthStore()
const router = useRouter()
watchEffect(() => {
  if (auth.user && auth.user.accountRole !== 'ADMIN') router.replace('/settings/account')
})

const searchApi = useSearch()
const { t } = useI18n()
const stats = ref<SearchStats | null>(null)
const loading = ref(true)
const busy = ref(false)
const message = ref('')
const isError = ref(false)

const TYPES: SearchType[] = ['qa', 'comment', 'project', 'update', 'test_case']

async function load() {
  loading.value = true
  try {
    stats.value = await searchApi.stats()
  } catch {
    stats.value = null
  } finally {
    loading.value = false
  }
}
onMounted(load)

async function reindex() {
  busy.value = true
  message.value = ''
  isError.value = false
  try {
    stats.value = await searchApi.reindex()
    message.value = t('shell.settings.searchIndex.done', { n: stats.value.total.toLocaleString() })
  } catch (e: any) {
    isError.value = true
    message.value = e?.data?.message ?? t('shell.settings.searchIndex.failed')
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div>
    <SettingsHeader :title="$t('shell.settings.searchIndex.title')" :subtitle="$t('shell.settings.searchIndex.subtitle')" />

    <div class="max-w-3xl space-y-4">
      <SettingRow :icon="DatabaseZap" :title="$t('shell.settings.searchIndex.docs')" :description="stats ? $t('shell.settings.searchIndex.total', { n: stats.total.toLocaleString() }) : (loading ? $t('common.state.loading') : $t('shell.settings.searchIndex.unavailable'))">
        <template #control>
          <button
            type="button"
            :disabled="busy"
            class="inline-flex items-center gap-1.5 whitespace-nowrap rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-xs font-medium text-slate-700 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-200 dark:hover:bg-slate-800/60"
            @click="reindex"
          >
            <Loader2 v-if="busy" class="h-3.5 w-3.5 animate-spin" />
            <RefreshCw v-else class="h-3.5 w-3.5" />
            {{ busy ? $t('shell.settings.searchIndex.reindexing') : $t('shell.settings.searchIndex.reindex') }}
          </button>
        </template>
        <div v-if="stats" class="mt-3 flex flex-wrap gap-2 pl-[52px]">
          <span v-for="tp in TYPES" :key="tp" class="rounded-full bg-white px-2.5 py-0.5 text-xs text-slate-600 ring-1 ring-slate-200 dark:bg-slate-900 dark:text-slate-300 dark:ring-slate-700">
            {{ $t(`shell.search.types.${tp}`) }} <span class="font-semibold">{{ (stats.counts[tp] ?? 0).toLocaleString() }}</span>
          </span>
        </div>
      </SettingRow>
      <p class="text-xs text-slate-400 dark:text-slate-500">{{ $t('shell.settings.searchIndex.hint') }}</p>
      <p v-if="message" :class="['text-xs', isError ? 'text-rose-500 dark:text-rose-400' : 'text-emerald-600 dark:text-emerald-400']">{{ message }}</p>
    </div>
  </div>
</template>
