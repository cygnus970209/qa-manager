<script setup lang="ts">
import { CheckCircle2, ExternalLink, GitBranch, Info, Loader2, Lock, RefreshCw, Unlink } from '@lucide/vue'
import type { GithubAppStatus, GithubRepo } from '~/types/api'

/**
 * GitHub App 연동 (전역, 관리자 전용).
 * GitHub 이 앱 생성 후 `/settings/github?code=...` 로 돌려보내면 code 를 교환한다 (backend GithubAppService.redirect_url).
 */
const { t } = useI18n()
const { confirmDialog } = useAppDialog()
const github = useGithub()
const route = useRoute()
const router = useRouter()

const githubApp = ref<GithubAppStatus | null>(null)
const githubLoading = ref(true)
const githubError = ref<string | null>(null)
const githubMsg = ref('')
const githubOrg = ref('')
const githubCreating = ref(false)
const githubRemoving = ref(false)
const githubRepos = ref<GithubRepo[]>([])
const reposLoading = ref(false)
const reposError = ref<string | null>(null)

onMounted(async () => {
  const code = route.query.code
  if (typeof code === 'string' && code) {
    await convertGithubCode(code)
  } else {
    await loadGithubApp()
  }
})

async function loadGithubApp() {
  githubLoading.value = true
  githubError.value = null
  try {
    githubApp.value = await github.appStatus()
    if (githubApp.value.configured) await loadGithubRepos()
  } catch (e: any) {
    githubError.value = e?.data?.message ?? t('admin.github.statusLoadFailed')
  } finally {
    githubLoading.value = false
  }
}
async function loadGithubRepos() {
  reposLoading.value = true
  reposError.value = null
  try {
    githubRepos.value = await github.listRepos()
  } catch (e: any) {
    reposError.value = e?.data?.message ?? t('admin.github.reposLoadFailed')
  } finally {
    reposLoading.value = false
  }
}
/** GitHub App 생성 후 리다이렉트로 받은 code 를 교환한다. */
async function convertGithubCode(code: string) {
  githubLoading.value = true
  githubError.value = null
  try {
    githubApp.value = await github.convert(code)
    githubMsg.value = t('admin.github.connectSuccess')
    if (githubApp.value.configured) await loadGithubRepos()
  } catch (e: any) {
    // code 는 일회용이라 재교환은 거의 항상 실패 — 앱 생성부터 다시 하도록 안내한다.
    githubError.value = e?.data?.message ?? t('admin.github.convertFailed')
    try {
      githubApp.value = await github.appStatus()
    } catch { /* 상태 조회 실패는 무시 */ }
  } finally {
    // 성공/실패와 무관하게 code 쿼리 제거 (재방문/새로고침 시 반복 재시도 방지).
    const q = { ...route.query }
    delete q.code
    await router.replace({ query: q })
    githubLoading.value = false
  }
}
/** manifest 응답을 hidden form POST 로 GitHub 에 제출한다 (브라우저 네비게이션 필수). */
function submitManifestForm(targetUrl: string, manifest: string) {
  const form = document.createElement('form')
  form.method = 'post'
  form.action = targetUrl
  const input = document.createElement('input')
  input.type = 'hidden'
  input.name = 'manifest'
  input.value = manifest
  form.appendChild(input)
  document.body.appendChild(form)
  form.submit()
}
async function createGithubApp() {
  githubCreating.value = true
  githubError.value = null
  githubMsg.value = ''
  try {
    const res = await github.createManifest(githubOrg.value.trim() || null, window.location.origin)
    // 페이지가 GitHub 으로 이동하므로 성공 시 로딩 상태를 유지한다.
    submitManifestForm(res.targetUrl, res.manifest)
  } catch (e: any) {
    githubError.value = e?.data?.message ?? t('admin.github.createFailed')
    githubCreating.value = false
  }
}
async function removeGithubApp() {
  if (!(await confirmDialog({ message: t('admin.github.disconnectConfirm'), danger: true }))) return
  githubRemoving.value = true
  githubError.value = null
  githubMsg.value = ''
  try {
    await github.removeApp()
    githubApp.value = { configured: false, appSlug: null, appName: null, installUrl: null }
    githubRepos.value = []
    githubMsg.value = t('admin.github.disconnected')
  } catch (e: any) {
    githubError.value = e?.data?.message ?? t('admin.github.disconnectFailed')
  } finally {
    githubRemoving.value = false
  }
}
</script>

<template>
  <div class="max-w-3xl space-y-5">
    <div v-if="githubLoading" class="flex items-center gap-2 py-6 text-sm text-slate-400 dark:text-slate-500">
      <Loader2 class="h-4 w-4 animate-spin" /> {{ $t('admin.github.checkingStatus') }}
    </div>

    <!-- 설정된 상태 -->
    <template v-else-if="githubApp?.configured">
      <div class="rounded-xl border border-slate-100 bg-slate-50 p-4 dark:border-slate-800 dark:bg-slate-800/50">
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-3">
            <div class="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg border border-slate-100 bg-white text-slate-500 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-400">
              <GitBranch class="h-4 w-4" />
            </div>
            <div>
              <p class="text-sm font-semibold text-slate-800 dark:text-slate-100">{{ githubApp.appName ?? 'GitHub App' }}</p>
              <p class="mt-0.5 text-xs text-slate-500 dark:text-slate-400">
                {{ $t('admin.github.connected') }}<template v-if="githubApp.appSlug"> · @{{ githubApp.appSlug }}</template>
              </p>
            </div>
          </div>
          <span class="inline-flex items-center gap-1 rounded-full bg-emerald-50 px-2.5 py-0.5 text-xs font-medium text-emerald-600 dark:bg-emerald-500/10 dark:text-emerald-400">
            <CheckCircle2 class="h-3.5 w-3.5" /> {{ $t('admin.github.connectedBadge') }}
          </span>
        </div>
        <a
          v-if="githubApp.installUrl"
          :href="githubApp.installUrl"
          target="_blank"
          rel="noopener noreferrer"
          class="mt-3 inline-flex items-center gap-1.5 text-xs font-medium text-emerald-600 hover:text-emerald-700 hover:underline dark:text-emerald-400 dark:hover:text-emerald-300"
        >
          <ExternalLink class="h-3.5 w-3.5" />
          {{ $t('admin.github.manageOnGithub') }}
        </a>
      </div>

      <!-- 연결된 저장소 -->
      <div class="rounded-xl border border-slate-100 bg-slate-50 p-4 dark:border-slate-800 dark:bg-slate-800/50">
        <div class="mb-3 flex items-center justify-between">
          <p class="text-sm font-semibold text-slate-800 dark:text-slate-100">{{ $t('admin.github.reposTitle') }}</p>
          <button
            type="button"
            :disabled="reposLoading"
            class="inline-flex items-center gap-1 rounded-md border border-slate-200 bg-white px-2.5 py-1 text-xs text-slate-600 hover:bg-slate-100 disabled:opacity-60 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-300 dark:hover:bg-slate-800"
            @click="loadGithubRepos"
          >
            <RefreshCw :class="['h-3.5 w-3.5', reposLoading ? 'animate-spin' : '']" />
            {{ $t('admin.github.refresh') }}
          </button>
        </div>
        <div v-if="reposLoading" class="flex items-center gap-2 py-3 text-xs text-slate-400 dark:text-slate-500">
          <Loader2 class="h-3.5 w-3.5 animate-spin" /> {{ $t('admin.github.reposLoading') }}
        </div>
        <p v-else-if="reposError" class="rounded bg-red-50 px-3 py-2 text-xs text-red-700 dark:bg-red-500/10 dark:text-red-300">{{ reposError }}</p>
        <p v-else-if="githubRepos.length === 0" class="py-2 text-xs text-slate-400 dark:text-slate-500">
          {{ $t('admin.github.reposEmpty') }}
        </p>
        <ul v-else class="divide-y divide-slate-100 overflow-hidden rounded-lg border border-slate-100 bg-white dark:divide-slate-800 dark:border-slate-800 dark:bg-slate-900">
          <li v-for="r in githubRepos" :key="`${r.installationId}:${r.fullName}`" class="flex items-center gap-2 px-3 py-2">
            <GitBranch class="h-3.5 w-3.5 shrink-0 text-slate-400 dark:text-slate-500" />
            <a
              :href="r.htmlUrl"
              target="_blank"
              rel="noopener noreferrer"
              class="min-w-0 flex-1 truncate text-sm text-slate-700 hover:text-emerald-600 hover:underline dark:text-slate-200 dark:hover:text-emerald-400"
            >{{ r.fullName }}</a>
            <span
              v-if="r.private"
              class="inline-flex items-center gap-1 rounded-full bg-amber-50 px-2 py-0.5 text-[11px] font-medium text-amber-600 dark:bg-amber-500/10 dark:text-amber-400"
            >
              <Lock class="h-3 w-3" /> {{ $t('admin.github.private') }}
            </span>
          </li>
        </ul>
      </div>

      <!-- 연동 해제 -->
      <div class="flex items-center gap-3">
        <button
          type="button"
          :disabled="githubRemoving"
          class="inline-flex items-center gap-1.5 rounded-lg border border-rose-200 bg-rose-50 px-4 py-2 text-sm font-medium text-rose-600 transition-colors hover:bg-rose-100 disabled:cursor-not-allowed disabled:opacity-60 dark:border-rose-500/30 dark:bg-rose-500/10 dark:text-rose-400 dark:hover:bg-rose-500/20"
          @click="removeGithubApp"
        >
          <Loader2 v-if="githubRemoving" class="h-4 w-4 animate-spin" />
          <Unlink v-else class="h-4 w-4" />
          {{ $t('admin.github.disconnect') }}
        </button>
        <span class="text-xs text-slate-400 dark:text-slate-500">{{ $t('admin.github.disconnectHint') }}</span>
      </div>
    </template>

    <!-- 미설정 상태 -->
    <template v-else>
      <div class="rounded-xl border border-blue-100 bg-blue-50/60 p-4 dark:border-blue-500/30 dark:bg-blue-500/10">
        <div class="flex items-start gap-3">
          <Info class="mt-0.5 h-4 w-4 shrink-0 text-blue-500 dark:text-blue-400" />
          <div class="space-y-2 text-sm text-slate-700 dark:text-slate-200">
            <p class="font-semibold text-slate-800 dark:text-slate-100">{{ $t('admin.github.setupTitle') }}</p>
            <ol class="list-decimal space-y-1 pl-4 text-xs text-slate-600 dark:text-slate-300">
              <i18n-t keypath="admin.github.setupStep1" scope="global" tag="li">
                <template #app><span class="font-medium">GitHub App</span></template>
              </i18n-t>
              <li>{{ $t('admin.github.setupStep2') }}</li>
              <li>{{ $t('admin.github.setupStep3') }}</li>
            </ol>
            <p class="text-xs text-slate-400 dark:text-slate-500">{{ $t('admin.github.setupFootnote') }}</p>
          </div>
        </div>
      </div>
      <label class="block">
        <span class="block text-xs font-medium text-slate-600 dark:text-slate-300">{{ $t('admin.github.orgLabel') }}</span>
        <input
          v-model="githubOrg"
          type="text"
          :placeholder="$t('admin.github.orgPlaceholder')"
          class="mt-1 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm focus:border-emerald-500 focus:outline-none focus:ring-2 focus:ring-emerald-500/20 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100 dark:placeholder-slate-500"
        />
      </label>
      <button
        type="button"
        :disabled="githubCreating"
        class="inline-flex items-center gap-1.5 whitespace-nowrap rounded-lg bg-slate-800 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-slate-900 disabled:cursor-not-allowed disabled:opacity-60 dark:bg-slate-700 dark:hover:bg-slate-600"
        @click="createGithubApp"
      >
        <Loader2 v-if="githubCreating" class="h-4 w-4 animate-spin" />
        <GitBranch v-else class="h-4 w-4" />
        {{ $t('admin.github.createApp') }}
      </button>
    </template>

    <p v-if="githubError" class="rounded bg-red-50 px-3 py-2 text-xs text-red-700 dark:bg-red-500/10 dark:text-red-300">{{ githubError }}</p>
    <p v-if="githubMsg" class="rounded bg-emerald-50 px-3 py-2 text-xs text-emerald-700 dark:bg-emerald-500/10 dark:text-emerald-300">{{ githubMsg }}</p>
  </div>
</template>
