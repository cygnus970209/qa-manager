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
} from '@lucide/vue'
import type { NotificationSettings, TeamsTestResult } from '~/types/api'
import TeamsTestResultModal from '~/components/feature/TeamsTestResultModal.vue'

type SubTab = 'notifications' | 'ms-teams'

const auth = useAuthStore()
const api = useApi()
const members = useMembers()

const subTab = ref<SubTab>('notifications')

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

      <!-- 공통 저장 바 -->
      <div class="mt-6 flex items-center gap-3 border-t border-slate-100 pt-4">
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
