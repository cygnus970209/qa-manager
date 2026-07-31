<script setup lang="ts">
import {
  Bell,
  Bug,
  MessageSquare,
  CornerDownRight,
  Moon,
  MonitorSmartphone,
  Send,
  Loader2,
  Info,
  CheckCircle2,
  GitBranch,
  ExternalLink,
  RefreshCw,
  Lock,
  Unlink,
} from '@lucide/vue'
import type { GithubAppStatus, GithubRepo, NotificationSettings, TeamsTestResult } from '~/types/api'
import TeamsTestResultModal from '~/components/feature/TeamsTestResultModal.vue'

type SubTab = 'notifications' | 'ms-teams' | 'github'
const SUB_KEYS: SubTab[] = ['notifications', 'ms-teams', 'github']

const auth = useAuthStore()
const api = useApi()
const members = useMembers()
const github = useGithub()

const route = useRoute()
const router = useRouter()
const subTab = ref<SubTab>(
  SUB_KEYS.includes(route.query.sub as SubTab) ? (route.query.sub as SubTab) : 'notifications',
)

const settings = reactive<NotificationSettings>({
  teamsNotifyEnabled: true,
  notifyQaEnabled: true,
  notifyCommentEnabled: true,
  notifyReplyEnabled: true,
  quietHoursStart: null,
  quietHoursEnd: null,
})

const loading = ref(true)
const saving = ref(false)
const savedMsg = ref('')
const savedError = ref(false)
const quietEnabled = ref(false)

// 알림 종류 토글 메타 (백엔드 type: qa / comment / reply)
const typeItems = [
  { key: 'notifyQaEnabled', label: 'QA 알림', description: 'QA 등록·상태 변경·배정 시 알림을 받습니다', icon: Bug },
  { key: 'notifyCommentEnabled', label: '코멘트 알림', description: '담당 QA에 코멘트가 달리면 알림을 받습니다', icon: MessageSquare },
  { key: 'notifyReplyEnabled', label: '답글 알림', description: '내 코멘트에 답글이 달리면 알림을 받습니다', icon: CornerDownRight },
] as const

onMounted(async () => {
  try {
    const s = await api<NotificationSettings>('/api/me/notification-settings')
    Object.assign(settings, s)
    quietEnabled.value = !!(s.quietHoursStart && s.quietHoursEnd)
  } catch {
    // 로드 실패 시 기본값 유지
  } finally {
    loading.value = false
  }

  // GitHub 앱 생성 후 리다이렉트(?code=)면 code 를 교환하고, 아니면 상태만 조회.
  const code = route.query.code
  if (typeof code === 'string' && code) {
    subTab.value = 'github'
    await convertGithubCode(code)
  } else {
    await loadGithubApp()
  }
})

async function save() {
  saving.value = true
  savedMsg.value = ''
  savedError.value = false
  try {
    const body: NotificationSettings = {
      teamsNotifyEnabled: settings.teamsNotifyEnabled,
      notifyQaEnabled: settings.notifyQaEnabled,
      notifyCommentEnabled: settings.notifyCommentEnabled,
      notifyReplyEnabled: settings.notifyReplyEnabled,
      quietHoursStart: quietEnabled.value ? (settings.quietHoursStart || '22:00') : null,
      quietHoursEnd: quietEnabled.value ? (settings.quietHoursEnd || '08:00') : null,
    }
    const s = await api<NotificationSettings>('/api/me/notification-settings', { method: 'PUT', body })
    Object.assign(settings, s)
    quietEnabled.value = !!(s.quietHoursStart && s.quietHoursEnd)
    savedMsg.value = '저장되었습니다.'
  } catch {
    savedError.value = true
    savedMsg.value = '저장에 실패했습니다.'
  } finally {
    saving.value = false
  }
}

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
    testResult.value = await members.teamsTest(auth.user.id)
  } finally {
    testLoading.value = false
  }
}

/* ─── GitHub 연동 ─── */
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

async function loadGithubApp() {
  githubLoading.value = true
  githubError.value = null
  try {
    githubApp.value = await github.appStatus()
    if (githubApp.value.configured) await loadGithubRepos()
  } catch (e: any) {
    githubError.value = e?.data?.message ?? 'GitHub 연동 상태를 불러오지 못했습니다.'
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
    reposError.value = e?.data?.message ?? '저장소 목록을 불러오지 못했습니다.'
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
    githubMsg.value = 'GitHub App이 성공적으로 연동되었습니다.'
    if (githubApp.value.configured) await loadGithubRepos()
  } catch (e: any) {
    // code 는 일회용이라 재교환은 거의 항상 실패 — 앱 생성부터 다시 하도록 안내한다.
    githubError.value = e?.data?.message
      ?? 'GitHub App 연동에 실패했습니다. "GitHub App 생성하기"부터 다시 시도해 주세요.'
    // 교환 실패 시 현재 상태라도 조회해 화면을 복구.
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
    githubError.value = e?.data?.message ?? 'GitHub App 생성 요청에 실패했습니다.'
    githubCreating.value = false
  }
}

async function removeGithubApp() {
  if (!confirm('GitHub 연동을 해제하시겠습니까?\n프로젝트별 저장소 연결과 이슈 연동이 더 이상 동작하지 않습니다.')) return
  githubRemoving.value = true
  githubError.value = null
  githubMsg.value = ''
  try {
    await github.removeApp()
    githubApp.value = { configured: false, appSlug: null, appName: null, installUrl: null }
    githubRepos.value = []
    githubMsg.value = 'GitHub 연동이 해제되었습니다.'
  } catch (e: any) {
    githubError.value = e?.data?.message ?? 'GitHub 연동 해제에 실패했습니다.'
  } finally {
    githubRemoving.value = false
  }
}
</script>

<template>
  <div class="p-4 md:p-6">
    <!-- Sub Tabs -->
    <div class="mb-6 flex w-fit items-center gap-1 rounded-lg bg-slate-100 p-1">
      <button
        type="button"
        :class="[
          'flex items-center gap-1.5 rounded-md px-4 py-2 text-sm font-medium transition-colors whitespace-nowrap',
          subTab === 'notifications' ? 'bg-white text-slate-800 shadow-sm' : 'text-slate-500 hover:text-slate-700',
        ]"
        @click="subTab = 'notifications'"
      >
        <Bell class="h-4 w-4" />
        알림 설정
      </button>
      <button
        type="button"
        :class="[
          'flex items-center gap-1.5 rounded-md px-4 py-2 text-sm font-medium transition-colors whitespace-nowrap',
          subTab === 'ms-teams' ? 'bg-white text-slate-800 shadow-sm' : 'text-slate-500 hover:text-slate-700',
        ]"
        @click="subTab = 'ms-teams'"
      >
        <MonitorSmartphone class="h-4 w-4" />
        MS Teams 설정
      </button>
      <button
        type="button"
        :class="[
          'flex items-center gap-1.5 rounded-md px-4 py-2 text-sm font-medium transition-colors whitespace-nowrap',
          subTab === 'github' ? 'bg-white text-slate-800 shadow-sm' : 'text-slate-500 hover:text-slate-700',
        ]"
        @click="subTab = 'github'"
      >
        <GitBranch class="h-4 w-4" />
        GitHub 설정
      </button>
    </div>

    <div v-if="loading" class="flex items-center gap-2 py-10 text-sm text-slate-400">
      <Loader2 class="h-4 w-4 animate-spin" /> 설정을 불러오는 중...
    </div>

    <template v-else>
      <!-- 알림 설정 -->
      <div v-show="subTab === 'notifications'" class="max-w-xl space-y-5">
        <p class="rounded-md bg-slate-50 px-3 py-2 text-xs text-slate-500">
          종류별 알림과 방해금지 시간대는 <span class="font-medium text-slate-600">Teams 발송</span>에만 적용됩니다.
          앱 내 알림(벨)은 항상 표시됩니다.
        </p>

        <!-- 종류별 토글 -->
        <div class="space-y-3">
          <div
            v-for="item in typeItems"
            :key="item.key"
            class="flex items-center justify-between rounded-xl border border-slate-100 bg-slate-50 p-4 transition-colors hover:border-slate-200"
          >
            <div class="flex items-start gap-3">
              <div class="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg border border-slate-100 bg-white text-slate-500">
                <component :is="item.icon" class="h-4 w-4" />
              </div>
              <div>
                <p class="text-sm font-semibold text-slate-800">{{ item.label }}</p>
                <p class="mt-0.5 text-xs text-slate-500">{{ item.description }}</p>
              </div>
            </div>
            <label class="relative ml-4 inline-flex shrink-0 cursor-pointer items-center">
              <input v-model="settings[item.key]" type="checkbox" class="peer sr-only" />
              <div class="peer h-6 w-11 rounded-full bg-slate-200 after:absolute after:left-[2px] after:top-[2px] after:h-5 after:w-5 after:rounded-full after:border after:border-slate-300 after:bg-white after:transition-all after:content-[''] peer-checked:bg-emerald-500 peer-checked:after:translate-x-full peer-checked:after:border-white peer-focus:outline-none" />
            </label>
          </div>
        </div>

        <!-- 방해금지 시간대 -->
        <div class="rounded-xl border border-slate-100 bg-slate-50 p-4">
          <div class="flex items-center justify-between">
            <div class="flex items-start gap-3">
              <div class="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg border border-slate-100 bg-white text-slate-500">
                <Moon class="h-4 w-4" />
              </div>
              <div>
                <p class="text-sm font-semibold text-slate-800">방해금지 시간대</p>
                <p class="mt-0.5 text-xs text-slate-500">이 시간대에는 Teams 알림을 보내지 않습니다 (앱 내 알림은 유지)</p>
              </div>
            </div>
            <label class="relative ml-4 inline-flex shrink-0 cursor-pointer items-center">
              <input v-model="quietEnabled" type="checkbox" class="peer sr-only" />
              <div class="peer h-6 w-11 rounded-full bg-slate-200 after:absolute after:left-[2px] after:top-[2px] after:h-5 after:w-5 after:rounded-full after:border after:border-slate-300 after:bg-white after:transition-all after:content-[''] peer-checked:bg-emerald-500 peer-checked:after:translate-x-full peer-checked:after:border-white peer-focus:outline-none" />
            </label>
          </div>
          <div v-if="quietEnabled" class="mt-4 flex items-center gap-3 pl-13">
            <div>
              <label class="mb-1 block text-xs font-medium text-slate-600">시작</label>
              <input
                v-model="settings.quietHoursStart"
                type="time"
                class="rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm focus:border-emerald-500 focus:outline-none focus:ring-2 focus:ring-emerald-500/20"
              />
            </div>
            <span class="mt-5 text-slate-400">~</span>
            <div>
              <label class="mb-1 block text-xs font-medium text-slate-600">종료</label>
              <input
                v-model="settings.quietHoursEnd"
                type="time"
                class="rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm focus:border-emerald-500 focus:outline-none focus:ring-2 focus:ring-emerald-500/20"
              />
            </div>
          </div>
          <p v-if="quietEnabled" class="mt-2 text-xs text-slate-400 pl-13">
            종료 시각이 시작보다 빠르면 자정을 넘기는 구간으로 처리됩니다 (예: 22:00 ~ 08:00).
          </p>
        </div>
      </div>

      <!-- MS Teams 설정 -->
      <div v-show="subTab === 'ms-teams'" class="max-w-xl space-y-5">
        <!-- 마스터 토글 -->
        <div class="flex items-center justify-between rounded-xl border border-slate-100 bg-slate-50 p-4">
          <div class="flex items-center gap-3">
            <div class="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg border border-slate-100 bg-white text-slate-500">
              <MonitorSmartphone class="h-4 w-4" />
            </div>
            <div>
              <p class="text-sm font-semibold text-slate-800">Teams 알림 받기</p>
              <p class="mt-0.5 text-xs text-slate-500">내 Teams로 1:1 알림을 받습니다 (전체 on/off)</p>
            </div>
          </div>
          <label class="relative ml-4 inline-flex shrink-0 cursor-pointer items-center">
            <input v-model="settings.teamsNotifyEnabled" type="checkbox" class="peer sr-only" />
            <div class="peer h-6 w-11 rounded-full bg-slate-200 after:absolute after:left-[2px] after:top-[2px] after:h-5 after:w-5 after:rounded-full after:border after:border-slate-300 after:bg-white after:transition-all after:content-[''] peer-checked:bg-emerald-500 peer-checked:after:translate-x-full peer-checked:after:border-white peer-focus:outline-none" />
          </label>
        </div>

        <!-- 봇 설치 안내 -->
        <div class="rounded-xl border border-blue-100 bg-blue-50/60 p-4">
          <div class="flex items-start gap-3">
            <Info class="mt-0.5 h-4 w-4 shrink-0 text-blue-500" />
            <div class="space-y-2 text-sm text-slate-700">
              <p class="font-semibold text-slate-800">Teams 알림을 받으려면 봇 설치가 필요합니다</p>
              <ol class="list-decimal space-y-1 pl-4 text-xs text-slate-600">
                <li>Teams 좌측 <span class="font-medium">앱</span> → <span class="font-medium">앱 관리</span> → <span class="font-medium">앱 업로드</span>에서 QA Manager 봇을 추가합니다.</li>
                <li>봇과의 1:1 채팅이 열리면 설치 완료입니다.</li>
                <li>아래 <span class="font-medium">테스트 발송</span>으로 정상 연결을 확인하세요.</li>
              </ol>
              <p class="text-xs text-slate-400">봇을 설치하지 않으면 알림이 전송되지 않습니다. 설치 방법은 관리자에게 문의하세요.</p>
            </div>
          </div>
        </div>

        <!-- 테스트 발송 -->
        <div class="flex items-center gap-3">
          <button
            type="button"
            :disabled="testLoading || !settings.teamsNotifyEnabled"
            class="inline-flex items-center gap-1.5 rounded-lg bg-emerald-500 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-emerald-600 disabled:cursor-not-allowed disabled:opacity-60 whitespace-nowrap"
            @click="sendTest"
          >
            <Loader2 v-if="testLoading" class="h-4 w-4 animate-spin" />
            <Send v-else class="h-4 w-4" />
            내게 테스트 발송
          </button>
          <span v-if="!settings.teamsNotifyEnabled" class="text-xs text-slate-400">Teams 알림을 켜야 발송할 수 있습니다.</span>
        </div>
      </div>

      <!-- GitHub 설정 -->
      <div v-show="subTab === 'github'" class="max-w-xl space-y-5">
        <div v-if="githubLoading" class="flex items-center gap-2 py-6 text-sm text-slate-400">
          <Loader2 class="h-4 w-4 animate-spin" /> GitHub 연동 상태를 확인하는 중...
        </div>

        <!-- 설정된 상태 -->
        <template v-else-if="githubApp?.configured">
          <div class="rounded-xl border border-slate-100 bg-slate-50 p-4">
            <div class="flex items-center justify-between">
              <div class="flex items-center gap-3">
                <div class="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg border border-slate-100 bg-white text-slate-500">
                  <GitBranch class="h-4 w-4" />
                </div>
                <div>
                  <p class="text-sm font-semibold text-slate-800">{{ githubApp.appName ?? 'GitHub App' }}</p>
                  <p class="mt-0.5 text-xs text-slate-500">
                    연동됨<template v-if="githubApp.appSlug"> · @{{ githubApp.appSlug }}</template>
                  </p>
                </div>
              </div>
              <span class="inline-flex items-center gap-1 rounded-full bg-emerald-50 px-2.5 py-0.5 text-xs font-medium text-emerald-600">
                <CheckCircle2 class="h-3.5 w-3.5" /> 연동 완료
              </span>
            </div>
            <a
              v-if="githubApp.installUrl"
              :href="githubApp.installUrl"
              target="_blank"
              rel="noopener noreferrer"
              class="mt-3 inline-flex items-center gap-1.5 text-xs font-medium text-emerald-600 hover:text-emerald-700 hover:underline"
            >
              <ExternalLink class="h-3.5 w-3.5" />
              GitHub에서 앱 설치·repo 권한 관리
            </a>
          </div>

          <!-- 연결된 저장소 -->
          <div class="rounded-xl border border-slate-100 bg-slate-50 p-4">
            <div class="mb-3 flex items-center justify-between">
              <p class="text-sm font-semibold text-slate-800">연결된 저장소</p>
              <button
                type="button"
                :disabled="reposLoading"
                class="inline-flex items-center gap-1 rounded-md border border-slate-200 bg-white px-2.5 py-1 text-xs text-slate-600 hover:bg-slate-100 disabled:opacity-60"
                @click="loadGithubRepos"
              >
                <RefreshCw :class="['h-3.5 w-3.5', reposLoading ? 'animate-spin' : '']" />
                새로고침
              </button>
            </div>
            <div v-if="reposLoading" class="flex items-center gap-2 py-3 text-xs text-slate-400">
              <Loader2 class="h-3.5 w-3.5 animate-spin" /> 저장소를 불러오는 중...
            </div>
            <p v-else-if="reposError" class="rounded bg-red-50 px-3 py-2 text-xs text-red-700">{{ reposError }}</p>
            <p v-else-if="githubRepos.length === 0" class="py-2 text-xs text-slate-400">
              연결된 저장소가 없습니다. 위 링크에서 앱을 설치하고 저장소 접근 권한을 추가하세요.
            </p>
            <ul v-else class="divide-y divide-slate-100 overflow-hidden rounded-lg border border-slate-100 bg-white">
              <li v-for="r in githubRepos" :key="`${r.installationId}:${r.fullName}`" class="flex items-center gap-2 px-3 py-2">
                <GitBranch class="h-3.5 w-3.5 shrink-0 text-slate-400" />
                <a
                  :href="r.htmlUrl"
                  target="_blank"
                  rel="noopener noreferrer"
                  class="min-w-0 flex-1 truncate text-sm text-slate-700 hover:text-emerald-600 hover:underline"
                >{{ r.fullName }}</a>
                <span
                  v-if="r.private"
                  class="inline-flex items-center gap-1 rounded-full bg-amber-50 px-2 py-0.5 text-[11px] font-medium text-amber-600"
                >
                  <Lock class="h-3 w-3" /> 비공개
                </span>
              </li>
            </ul>
          </div>

          <!-- 연동 해제 -->
          <div class="flex items-center gap-3">
            <button
              type="button"
              :disabled="githubRemoving"
              class="inline-flex items-center gap-1.5 rounded-lg border border-rose-200 bg-rose-50 px-4 py-2 text-sm font-medium text-rose-600 transition-colors hover:bg-rose-100 disabled:cursor-not-allowed disabled:opacity-60"
              @click="removeGithubApp"
            >
              <Loader2 v-if="githubRemoving" class="h-4 w-4 animate-spin" />
              <Unlink v-else class="h-4 w-4" />
              연동 해제
            </button>
            <span class="text-xs text-slate-400">GitHub 쪽의 앱은 삭제되지 않으며, 이 서비스와의 연결만 끊습니다.</span>
          </div>
        </template>

        <!-- 미설정 상태 -->
        <template v-else>
          <div class="rounded-xl border border-blue-100 bg-blue-50/60 p-4">
            <div class="flex items-start gap-3">
              <Info class="mt-0.5 h-4 w-4 shrink-0 text-blue-500" />
              <div class="space-y-2 text-sm text-slate-700">
                <p class="font-semibold text-slate-800">GitHub 이슈트래킹 연동</p>
                <ol class="list-decimal space-y-1 pl-4 text-xs text-slate-600">
                  <li>아래 버튼을 누르면 GitHub으로 이동해 전용 <span class="font-medium">GitHub App</span>을 생성합니다.</li>
                  <li>생성 후 앱을 설치하고 연동할 저장소를 선택하면 설정이 완료됩니다.</li>
                  <li>프로젝트에 저장소를 연결하면 QA 생성 시 GitHub 이슈를 함께 만들 수 있습니다.</li>
                </ol>
                <p class="text-xs text-slate-400">커밋 메시지에 #이슈번호를 남기면 QA 상세에서 관련 커밋을 확인할 수 있습니다.</p>
              </div>
            </div>
          </div>

          <label class="block">
            <span class="block text-xs font-medium text-slate-600">GitHub 조직명 (개인 계정에 설치하려면 비워두세요)</span>
            <input
              v-model="githubOrg"
              type="text"
              placeholder="예: my-organization"
              class="mt-1 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm focus:border-emerald-500 focus:outline-none focus:ring-2 focus:ring-emerald-500/20"
            />
          </label>

          <button
            type="button"
            :disabled="githubCreating"
            class="inline-flex items-center gap-1.5 rounded-lg bg-slate-800 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-slate-900 disabled:cursor-not-allowed disabled:opacity-60 whitespace-nowrap"
            @click="createGithubApp"
          >
            <Loader2 v-if="githubCreating" class="h-4 w-4 animate-spin" />
            <GitBranch v-else class="h-4 w-4" />
            GitHub App 생성하기
          </button>
        </template>

        <p v-if="githubError" class="rounded bg-red-50 px-3 py-2 text-xs text-red-700">{{ githubError }}</p>
        <p v-if="githubMsg" class="rounded bg-emerald-50 px-3 py-2 text-xs text-emerald-700">{{ githubMsg }}</p>
      </div>

      <!-- 공통 저장 바 (알림/Teams 설정 전용) -->
      <div v-if="subTab !== 'github'" class="mt-6 flex items-center gap-3 border-t border-slate-100 pt-4">
        <button
          type="button"
          :disabled="saving"
          class="inline-flex items-center gap-1.5 rounded-lg bg-slate-800 px-5 py-2 text-sm font-medium text-white transition-colors hover:bg-slate-900 disabled:cursor-not-allowed disabled:opacity-60"
          @click="save"
        >
          <Loader2 v-if="saving" class="h-4 w-4 animate-spin" />
          <CheckCircle2 v-else class="h-4 w-4" />
          저장
        </button>
        <span v-if="savedMsg" :class="['text-xs', savedError ? 'text-rose-500' : 'text-emerald-600']">{{ savedMsg }}</span>
      </div>
    </template>

    <TeamsTestResultModal
      :open="testOpen"
      :loading="testLoading"
      :result="testResult"
      :member-name="auth.user?.name"
      @close="testOpen = false"
    />
  </div>
</template>
