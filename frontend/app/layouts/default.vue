<script setup lang="ts">
import { Bell, Menu, ShieldCheck } from '@lucide/vue'
import AppSidebar from '~/components/feature/AppSidebar.vue'

/**
 * 기본 레이아웃: 왼쪽 사이드바 + 본문.
 * - md 이상: 사이드바가 화면 왼쪽에 고정(sticky, 화면 높이). 본문은 창 스크롤.
 * - md 미만: 상단 바(햄버거) + 드로어 오버레이.
 * - 알림 스트림/사이드바 데이터는 로그인 상태에서 여기서 시작한다 (예전 AppNavbar 역할).
 * - ⌘/Ctrl+B 로 사이드바 접기/펼치기.
 */
const auth = useAuthStore()
const notifs = useNotificationsStore()
const sidebar = useSidebarStore()
const route = useRoute()

async function start() {
  await Promise.all([
    notifs.load().catch(() => { /* 알림 로드 실패는 배지만 비운다 */ }),
    sidebar.ensureLoaded().catch(() => { /* 사이드바는 다음 reload 에서 복구 */ }),
  ])
  notifs.connect()
}

onMounted(() => {
  sidebar.initPref()
  if (auth.isAuthenticated) start()
})
watch(() => auth.isAuthenticated, (v) => {
  if (v) start()
  else {
    notifs.disconnect()
    sidebar.reset()
  }
})
onBeforeUnmount(() => notifs.disconnect())

function onKeydown(e: KeyboardEvent) {
  if (!(e.metaKey || e.ctrlKey) || e.shiftKey || e.altKey || e.key.toLowerCase() !== 'b') return
  const t = e.target as HTMLElement | null
  if (t && (t.tagName === 'INPUT' || t.tagName === 'TEXTAREA' || t.isContentEditable)) return
  e.preventDefault()
  sidebar.toggle()
}
onMounted(() => window.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))
</script>

<template>
  <div class="flex min-h-screen bg-gray-50 text-gray-900 dark:bg-slate-950 dark:text-slate-100">
    <!-- 데스크톱 사이드바 (md 이상) -->
    <!-- z-20: sticky 는 스태킹 컨텍스트를 만들어, 뒤에 오는 본문의 sticky 패널(QA 상세 필터)이 사이드바 팝오버를 덮지 않게 위로 올린다 -->
    <div class="sticky top-0 z-20 hidden h-screen shrink-0 md:block">
      <AppSidebar />
    </div>

    <!-- 모바일 드로어 -->
    <Teleport to="body">
      <div v-if="sidebar.mobileOpen" class="fixed inset-0 z-40 md:hidden">
        <div class="absolute inset-0 bg-slate-900/40" @click="sidebar.mobileOpen = false" />
        <div class="absolute inset-y-0 left-0 shadow-xl">
          <AppSidebar mode="drawer" />
        </div>
      </div>
    </Teleport>

    <div class="flex min-w-0 flex-1 flex-col">
      <!-- 모바일 상단 바 -->
      <header class="sticky top-0 z-30 flex h-[53px] items-center gap-2 border-b border-slate-200 bg-white px-3 dark:border-slate-800 dark:bg-slate-900 md:hidden">
        <button
          type="button"
          class="flex h-9 w-9 items-center justify-center rounded-md text-slate-600 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800"
          :aria-label="$t('shell.sidebar.openMenu')"
          @click="sidebar.mobileOpen = true"
        >
          <Menu class="h-5 w-5" />
        </button>
        <NuxtLink to="/" class="flex items-center gap-2">
          <ShieldCheck class="h-5 w-5 text-emerald-600 dark:text-emerald-400" />
          <span class="text-sm font-semibold tracking-tight">{{ $t('common.appName') }}</span>
        </NuxtLink>
        <NuxtLink
          to="/notifications"
          class="relative ml-auto flex h-9 w-9 items-center justify-center rounded-md text-slate-600 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800"
          :aria-label="$t('shell.nav.notifications')"
        >
          <Bell class="h-4 w-4" />
          <span
            v-if="notifs.unreadCount > 0"
            class="absolute right-1 top-1 inline-flex h-4 min-w-[16px] items-center justify-center rounded-full bg-rose-500 px-1 text-[10px] font-medium text-white"
          >{{ notifs.unreadCount }}</span>
        </NuxtLink>
      </header>

      <main :class="route.meta.fullBleed ? 'flex min-h-0 flex-1 flex-col' : 'mx-auto w-full max-w-[1400px] flex-1 px-4 py-6 md:px-6'">
        <slot />
      </main>
    </div>
  </div>
</template>
