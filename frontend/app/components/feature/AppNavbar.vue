<script setup lang="ts">
import { Bell, LogOut, ShieldCheck, UserRound } from '@lucide/vue'
import ProfileModal from '~/components/feature/ProfileModal.vue'
import { timeAgo } from '~/utils/format'

const auth = useAuthStore()
const notifs = useNotificationsStore()
const router = useRouter()

const dropdownOpen = ref(false)
const dropdownRef = ref<HTMLElement | null>(null)
const profileOpen = ref(false)

onMounted(async () => {
  if (auth.isAuthenticated) {
    await notifs.load()
    notifs.connect()
  }
})
watch(() => auth.isAuthenticated, async (v) => {
  if (v) {
    await notifs.load()
    notifs.connect()
  } else {
    notifs.disconnect()
  }
})
onBeforeUnmount(() => notifs.disconnect())

// 바깥 클릭으로 드롭다운 닫기
function onDocClick(e: MouseEvent) {
  if (!dropdownRef.value) return
  if (!dropdownRef.value.contains(e.target as Node)) dropdownOpen.value = false
}
onMounted(() => document.addEventListener('mousedown', onDocClick))
onBeforeUnmount(() => document.removeEventListener('mousedown', onDocClick))

async function onLogout() {
  await auth.logout()
  router.push('/auth/login')
}

async function onClickNotif(id: number, qaId: number | null, projectId: number | null) {
  if (!notifs.items.find((n) => n.id === id)?.read) {
    await notifs.markRead(id)
  }
  if (qaId) router.push(`/qa/${qaId}`)
  else if (projectId) router.push(`/project/${projectId}`)
  dropdownOpen.value = false
}
</script>

<template>
  <header class="sticky top-0 z-30 border-b border-slate-200 bg-white">
    <div class="mx-auto flex h-[53px] max-w-7xl items-center justify-between px-4">
      <NuxtLink to="/" class="flex items-center gap-2">
        <ShieldCheck class="h-5 w-5 text-emerald-600" />
        <span class="text-sm font-semibold tracking-tight">QA Manager</span>
      </NuxtLink>

      <nav class="flex items-center gap-1 text-sm">
        <NuxtLink to="/" class="rounded px-2 py-1 hover:bg-slate-100">대시보드</NuxtLink>
        <NuxtLink to="/admin" class="rounded px-2 py-1 hover:bg-slate-100">관리</NuxtLink>
      </nav>

      <div class="flex items-center gap-2">
        <div ref="dropdownRef" class="relative">
          <button
            type="button"
            class="relative rounded p-2 hover:bg-slate-100"
            aria-label="알림"
            @click="dropdownOpen = !dropdownOpen"
          >
            <Bell class="h-4 w-4" />
            <span
              v-if="notifs.unreadCount > 0"
              class="absolute right-1 top-1 inline-flex h-4 min-w-[16px] items-center justify-center rounded-full bg-rose-500 px-1 text-[10px] font-medium text-white"
            >
              {{ notifs.unreadCount }}
            </span>
          </button>
          <div
            v-if="dropdownOpen"
            class="absolute right-0 mt-2 w-80 overflow-hidden rounded-lg border border-slate-200 bg-white shadow-lg"
          >
            <div class="flex items-center justify-between border-b border-slate-100 px-3 py-2">
              <span class="text-xs font-medium text-slate-500">알림</span>
              <button
                v-if="notifs.unreadCount > 0"
                type="button"
                class="text-xs font-medium text-emerald-600 hover:text-emerald-700"
                @click="notifs.markAllRead()"
              >
                모두 읽기
              </button>
            </div>
            <ul v-if="notifs.items.length > 0" class="max-h-80 overflow-y-auto divide-y divide-slate-100">
              <li
                v-for="n in notifs.items"
                :key="n.id"
                :class="[
                  'cursor-pointer px-3 py-2 hover:bg-slate-50',
                  !n.read && 'bg-emerald-50/40',
                ]"
                @click="onClickNotif(n.id, n.qaItemId, n.projectId)"
              >
                <p class="line-clamp-2 text-sm text-slate-700">{{ n.message }}</p>
                <p class="mt-0.5 text-[11px] text-slate-400">
                  <span v-if="n.actorName">{{ n.actorName }} · </span>
                  <span v-if="n.projectName">{{ n.projectName }} · </span>
                  {{ timeAgo(n.createdAt) }}
                </p>
              </li>
            </ul>
            <p v-else class="px-3 py-6 text-center text-xs text-slate-400">알림이 없습니다.</p>
          </div>
        </div>

        <div v-if="auth.user" class="flex items-center gap-1 border-l border-slate-200 pl-2">
          <button
            type="button"
            class="flex items-center gap-2 rounded px-1.5 py-1 hover:bg-slate-100"
            aria-label="내 정보 수정"
            @click="profileOpen = true"
          >
            <img
              v-if="auth.user.avatarUrl"
              :src="auth.user.avatarUrl"
              :alt="auth.user.name"
              class="h-7 w-7 rounded-full bg-slate-100 object-cover"
            />
            <span
              v-else
              class="flex h-7 w-7 items-center justify-center rounded-full bg-slate-100 text-slate-400"
            >
              <UserRound class="h-4 w-4" />
            </span>
            <span class="hidden text-left text-xs sm:block">
              <span class="block font-medium">{{ auth.user.name }}</span>
              <span class="block text-slate-500">{{ auth.user.role ?? '' }}</span>
            </span>
          </button>
          <button
            type="button"
            class="rounded p-2 text-slate-500 hover:bg-slate-100 hover:text-slate-900"
            aria-label="로그아웃"
            @click="onLogout"
          >
            <LogOut class="h-4 w-4" />
          </button>
        </div>
      </div>
    </div>
    <ProfileModal :open="profileOpen" @close="profileOpen = false" />
  </header>
</template>
