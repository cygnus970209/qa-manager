<script setup lang="ts">
import { Bell, GitBranch, Languages, LogOut, Monitor, MonitorSmartphone, Palette, UserRound, Users, X } from '@lucide/vue'

/**
 * 설정 레이아웃 — 앱 사이드바 없이 전체 화면으로 열린다 (Discord 식).
 * 왼쪽: 세 묶음(사용자 설정 / 앱 설정 / 관리자)의 항목 목록 + 로그아웃. 오른쪽: 항목 내용 + 닫기(ESC).
 * 닫기는 설정에 들어오기 전 화면으로 돌아간다 (middleware/settings-return.global.ts).
 */
const auth = useAuthStore()
const notifs = useNotificationsStore()
const sidebar = useSidebarStore()
const route = useRoute()
const router = useRouter()
const desktop = useDesktop()
const returnTo = useState<string>('settings-return-to', () => '/')

const isAdmin = computed(() => auth.user?.accountRole === 'ADMIN')

interface Item { to: string; icon: unknown; label: string }
const groups = computed<{ label: string; items: Item[] }[]>(() => {
  const g = [
    {
      label: 'shell.settings.groups.user',
      items: [
        { to: '/settings/account', icon: UserRound, label: 'shell.settings.items.account' },
        { to: '/settings/notifications', icon: Bell, label: 'shell.settings.items.notifications' },
        { to: '/settings/teams', icon: MonitorSmartphone, label: 'shell.settings.items.teams' },
      ],
    },
    {
      label: 'shell.settings.groups.app',
      items: [
        { to: '/settings/appearance', icon: Palette, label: 'shell.settings.items.appearance' },
        { to: '/settings/language', icon: Languages, label: 'shell.settings.items.language' },
        { to: '/settings/desktop', icon: Monitor, label: 'shell.settings.items.desktop' },
      ],
    },
  ]
  if (isAdmin.value) {
    g.push({
      label: 'shell.settings.groups.admin',
      items: [
        { to: '/settings/members', icon: Users, label: 'shell.settings.items.members' },
        { to: '/settings/github', icon: GitBranch, label: 'shell.settings.items.github' },
      ],
    })
  }
  return g
})

function isActive(to: string) {
  return route.path === to || route.path.startsWith(to + '/')
}

function close() {
  router.push(returnTo.value || '/')
}

function onKeydown(e: KeyboardEvent) {
  if (e.key !== 'Escape') return
  const t = e.target as HTMLElement | null
  if (t && (t.tagName === 'INPUT' || t.tagName === 'TEXTAREA' || t.tagName === 'SELECT' || t.isContentEditable)) return
  close()
}
onMounted(() => window.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))

async function onLogout() {
  await auth.logout()
  notifs.disconnect()
  sidebar.reset()
  router.push('/auth/login')
}

const version = ref<string | null>(null)
onMounted(async () => {
  version.value = await desktop.version()
})

const itemBase = 'flex h-8 items-center gap-2.5 rounded-md px-2.5 text-sm transition-colors'
const itemIdle = 'text-slate-700 hover:bg-slate-100 dark:text-slate-200 dark:hover:bg-slate-800'
const itemActive = 'bg-emerald-50 font-medium text-emerald-700 dark:bg-emerald-500/10 dark:text-emerald-400'
</script>

<template>
  <div class="flex min-h-screen bg-white text-gray-900 dark:bg-slate-950 dark:text-slate-100">
    <!-- 왼쪽 항목 목록 (md 이상) -->
    <div class="hidden w-[260px] shrink-0 justify-end border-r border-slate-200 bg-slate-50 py-12 pl-4 pr-2 dark:border-slate-800 dark:bg-slate-900 md:flex xl:w-[300px]">
      <nav class="flex w-full max-w-[218px] flex-col gap-0.5">
        <template v-for="(g, gi) in groups" :key="g.label">
          <div v-if="gi > 0" class="mx-2.5 my-2 h-px bg-slate-200 dark:bg-slate-800" />
          <p class="px-2.5 pb-1 pt-2 text-[11px] font-semibold uppercase tracking-wider text-slate-400 dark:text-slate-500">{{ $t(g.label) }}</p>
          <NuxtLink v-for="it in g.items" :key="it.to" :to="it.to" :class="[itemBase, isActive(it.to) ? itemActive : itemIdle]">
            <component :is="it.icon" :class="['h-4 w-4 shrink-0', isActive(it.to) ? 'text-emerald-600 dark:text-emerald-400' : 'text-slate-400 dark:text-slate-500']" />
            <span class="truncate">{{ $t(it.label) }}</span>
          </NuxtLink>
        </template>
        <div class="mx-2.5 my-2 h-px bg-slate-200 dark:bg-slate-800" />
        <button type="button" :class="[itemBase, 'text-rose-600 hover:bg-rose-50 dark:text-rose-400 dark:hover:bg-rose-500/10']" @click="onLogout">
          <LogOut class="h-4 w-4 shrink-0" />
          <span>{{ $t('common.actions.logout') }}</span>
        </button>
        <p class="mt-auto px-2.5 pt-4 text-[11px] text-slate-400 dark:text-slate-500">
          {{ $t('common.appName') }}<template v-if="version"> v{{ version }} · {{ $t('shell.settings.items.desktop') }}</template>
        </p>
      </nav>
    </div>

    <div class="flex min-w-0 flex-1 flex-col">
      <!-- 모바일 상단 바 + 항목 칩 -->
      <div class="border-b border-slate-200 bg-slate-50 dark:border-slate-800 dark:bg-slate-900 md:hidden">
        <div class="flex h-[53px] items-center justify-between px-4">
          <span class="text-sm font-semibold">{{ $t('shell.settings.title') }}</span>
          <button type="button" class="flex h-9 w-9 items-center justify-center rounded-md text-slate-500 hover:bg-slate-100 dark:text-slate-400 dark:hover:bg-slate-800" :aria-label="$t('shell.settings.close')" @click="close">
            <X class="h-5 w-5" />
          </button>
        </div>
        <div class="flex gap-1.5 overflow-x-auto px-4 pb-3">
          <template v-for="g in groups" :key="g.label">
            <NuxtLink
              v-for="it in g.items"
              :key="it.to"
              :to="it.to"
              :class="[
                'shrink-0 whitespace-nowrap rounded-full px-3 py-1 text-xs font-medium',
                isActive(it.to) ? 'bg-emerald-50 text-emerald-700 dark:bg-emerald-500/10 dark:text-emerald-400' : 'bg-white text-slate-600 dark:bg-slate-800 dark:text-slate-300',
              ]"
            >{{ $t(it.label) }}</NuxtLink>
          </template>
        </div>
      </div>

      <div class="flex min-w-0 flex-1 px-4 py-6 md:px-8 md:py-12">
        <div class="min-w-0 max-w-[1280px] flex-1">
          <slot />
        </div>
        <div class="ml-5 hidden shrink-0 flex-col items-center gap-1.5 md:flex">
          <button
            type="button"
            class="flex h-9 w-9 items-center justify-center rounded-full border-2 border-slate-300 text-slate-500 transition-colors hover:border-slate-400 hover:text-slate-800 dark:border-slate-700 dark:text-slate-400 dark:hover:border-slate-500 dark:hover:text-slate-100"
            :aria-label="$t('shell.settings.close')"
            @click="close"
          >
            <X class="h-[18px] w-[18px]" />
          </button>
          <span class="text-[11px] font-semibold text-slate-500 dark:text-slate-400">ESC</span>
        </div>
      </div>
    </div>
  </div>
</template>
