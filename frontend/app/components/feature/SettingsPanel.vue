<script setup lang="ts">
import {
  Bell,
  Calendar,
  CheckCircle2,
  AlertCircle,
  Folder,
  Loader2,
  Mail,
  MonitorSmartphone,
  Plug,
  Bug,
} from '@lucide/vue'

type SubTab = 'notifications' | 'ms-teams'

const subTab = ref<SubTab>('notifications')

const notifications = ref([
  { key: 'email', label: '이메일 알림', description: '중요한 업데이트가 있을 때 이메일로 알림을 받습니다', icon: Mail, enabled: true },
  { key: 'push', label: '웹 푸시 알림', description: '브라우저 푸시 알림을 받습니다', icon: MonitorSmartphone, enabled: false },
  { key: 'qa_assigned', label: 'QA 할당 알림', description: '새로운 QA가 나에게 할당되면 알림을 받습니다', icon: Bug, enabled: true },
  { key: 'qa_resolved', label: 'QA 해결 알림', description: '내가 등록한 QA가 해결되면 알림을 받습니다', icon: CheckCircle2, enabled: true },
  { key: 'project_updates', label: '프로젝트 업데이트', description: '프로젝트 상태가 변경되면 알림을 받습니다', icon: Folder, enabled: true },
  { key: 'daily_digest', label: '일일 요약', description: '매일 아침 전날 활동 요약을 받습니다', icon: Calendar, enabled: false },
])

const msTeams = reactive({
  enabled: false,
  webhookUrl: '',
  channelName: '',
  status: 'idle' as 'idle' | 'testing' | 'success' | 'error',
  statusMessage: '',
})

function toggleNotification(key: string) {
  const it = notifications.value.find((n) => n.key === key)
  if (it) it.enabled = !it.enabled
}

function handleTestConnection() {
  if (!msTeams.webhookUrl.trim()) {
    msTeams.status = 'error'
    msTeams.statusMessage = 'Webhook URL을 입력해주세요.'
    return
  }
  msTeams.status = 'testing'
  msTeams.statusMessage = '연결 테스트 중...'
  setTimeout(() => {
    const success = Math.random() > 0.2
    msTeams.status = success ? 'success' : 'error'
    msTeams.statusMessage = success ? '연결에 성공했습니다.' : '연결에 실패했습니다. URL을 확인해주세요.'
  }, 1500)
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

    <p class="mb-4 rounded-md bg-amber-50 px-3 py-2 text-xs text-amber-700">
      ⓘ 현재는 UI 미리보기입니다. 저장 기능은 추후 연동됩니다.
    </p>

    <!-- Notifications -->
    <div v-if="subTab === 'notifications'" class="space-y-3">
      <div
        v-for="item in notifications"
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
          <input
            type="checkbox"
            class="peer sr-only"
            :checked="item.enabled"
            @change="toggleNotification(item.key)"
          />
          <div class="peer h-6 w-11 rounded-full bg-slate-200 after:absolute after:left-[2px] after:top-[2px] after:h-5 after:w-5 after:rounded-full after:border after:border-slate-300 after:bg-white after:transition-all after:content-[''] peer-checked:bg-emerald-500 peer-checked:after:translate-x-full peer-checked:after:border-white peer-focus:outline-none" />
        </label>
      </div>
    </div>

    <!-- MS Teams -->
    <div v-if="subTab === 'ms-teams'" class="max-w-xl space-y-5">
      <div class="flex items-center justify-between rounded-xl border border-slate-100 bg-slate-50 p-4">
        <div class="flex items-center gap-3">
          <div class="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg border border-slate-100 bg-white text-slate-500">
            <MonitorSmartphone class="h-4 w-4" />
          </div>
          <div>
            <p class="text-sm font-semibold text-slate-800">MS Teams 연동 활성화</p>
            <p class="mt-0.5 text-xs text-slate-500">팀 채널로 알림을 전송합니다</p>
          </div>
        </div>
        <label class="relative ml-4 inline-flex shrink-0 cursor-pointer items-center">
          <input
            v-model="msTeams.enabled"
            type="checkbox"
            class="peer sr-only"
          />
          <div class="peer h-6 w-11 rounded-full bg-slate-200 after:absolute after:left-[2px] after:top-[2px] after:h-5 after:w-5 after:rounded-full after:border after:border-slate-300 after:bg-white after:transition-all after:content-[''] peer-checked:bg-emerald-500 peer-checked:after:translate-x-full peer-checked:after:border-white peer-focus:outline-none" />
        </label>
      </div>

      <div class="space-y-4">
        <div>
          <label class="mb-1.5 block text-sm font-medium text-slate-700">Webhook URL</label>
          <input
            v-model="msTeams.webhookUrl"
            type="url"
            placeholder="https://outlook.office.com/webhook/..."
            class="w-full rounded-lg border border-slate-200 bg-white px-3 py-2.5 text-sm transition-colors focus:border-emerald-500 focus:outline-none focus:ring-2 focus:ring-emerald-500/20"
            @input="msTeams.status = 'idle'; msTeams.statusMessage = ''"
          />
          <p class="mt-1 text-xs text-slate-400">Incoming Webhook 커넥터에서 제공한 URL을 입력하세요</p>
        </div>

        <div>
          <label class="mb-1.5 block text-sm font-medium text-slate-700">채널 이름</label>
          <input
            v-model="msTeams.channelName"
            type="text"
            placeholder="예: 프로젝트 알림 채널"
            class="w-full rounded-lg border border-slate-200 bg-white px-3 py-2.5 text-sm transition-colors focus:border-emerald-500 focus:outline-none focus:ring-2 focus:ring-emerald-500/20"
          />
        </div>

        <div class="flex items-center gap-3 pt-1">
          <button
            type="button"
            :disabled="msTeams.status === 'testing'"
            class="inline-flex items-center gap-1.5 rounded-lg bg-emerald-500 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-emerald-600 disabled:cursor-not-allowed disabled:opacity-60 whitespace-nowrap"
            @click="handleTestConnection"
          >
            <template v-if="msTeams.status === 'testing'">
              <Loader2 class="h-4 w-4 animate-spin" />
              테스트 중...
            </template>
            <template v-else>
              <Plug class="h-4 w-4" />
              연결 테스트
            </template>
          </button>
          <span
            v-if="msTeams.status === 'testing'"
            class="inline-flex items-center gap-1 rounded-full bg-amber-50 px-2 py-1 text-xs font-medium text-amber-600"
          >
            <Loader2 class="h-3 w-3 animate-spin" /> 연결 테스트 중
          </span>
          <span
            v-else-if="msTeams.status === 'success'"
            class="inline-flex items-center gap-1 rounded-full bg-emerald-50 px-2 py-1 text-xs font-medium text-emerald-600"
          >
            <CheckCircle2 class="h-3 w-3" /> 연결 성공
          </span>
          <span
            v-else-if="msTeams.status === 'error'"
            class="inline-flex items-center gap-1 rounded-full bg-rose-50 px-2 py-1 text-xs font-medium text-rose-600"
          >
            <AlertCircle class="h-3 w-3" /> 연결 실패
          </span>
        </div>

        <p
          v-if="msTeams.statusMessage"
          :class="['text-xs', msTeams.status === 'error' ? 'text-rose-500' : 'text-slate-500']"
        >
          {{ msTeams.statusMessage }}
        </p>
      </div>
    </div>
  </div>
</template>
