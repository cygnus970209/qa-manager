<script setup lang="ts">
import { Bell, Check, Globe, LogOut, Monitor, Moon, Settings, ShieldCheck, Sun, UserRound } from '@lucide/vue'
import ProfileModal from '~/components/feature/ProfileModal.vue'
import { timeAgo } from '~/utils/format'

const auth = useAuthStore()
const notifs = useNotificationsStore()
const router = useRouter()

const dropdownOpen = ref(false)
const dropdownRef = ref<HTMLElement | null>(null)
const profileOpen = ref(false)

const { locale, locales, setLocale } = useI18n()
const langOpen = ref(false)
const langRef = ref<HTMLElement | null>(null)

async function onSelectLocale(code: 'ko' | 'en') {
  langOpen.value = false
  if (code !== locale.value) await setLocale(code)
}

// 테마 (vueuse 의 useColorMode 와 이름이 겹쳐 모듈 주입 $colorMode 를 직접 사용)
const colorMode = useNuxtApp().$colorMode
const themeOpen = ref(false)
const themeRef = ref<HTMLElement | null>(null)
const themeOptions = [
  { value: 'light', icon: Sun, labelKey: 'common.theme.light' },
  { value: 'dark', icon: Moon, labelKey: 'common.theme.dark' },
  { value: 'system', icon: Monitor, labelKey: 'common.theme.system' },
] as const
function onSelectTheme(v: 'light' | 'dark' | 'system') {
  colorMode.preference = v
  themeOpen.value = false
}

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
  const t = e.target as Node
  if (dropdownRef.value && !dropdownRef.value.contains(t)) dropdownOpen.value = false
  if (langRef.value && !langRef.value.contains(t)) langOpen.value = false
  if (themeRef.value && !themeRef.value.contains(t)) themeOpen.value = false
}
onMounted(() => document.addEventListener('mousedown', onDocClick))
onBeforeUnmount(() => document.removeEventListener('mousedown', onDocClick))

async function onLogout() {
  await auth.logout()
  router.push('/auth/login')
}

async function onClickNotif(id: number) {
  dropdownOpen.value = false
  await notifs.openNotification(id)
}
</script>

<template>
  <header class="sticky top-0 z-30 border-b border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-900">
    <div class="mx-auto flex h-[53px] max-w-7xl items-center justify-between px-4">
      <NuxtLink to="/" class="flex items-center gap-2">
        <ShieldCheck class="h-5 w-5 text-emerald-600 dark:text-emerald-400" />
        <span class="text-sm font-semibold tracking-tight">{{ $t('common.appName') }}</span>
      </NuxtLink>

      <nav class="flex items-center gap-1 text-sm">
        <NuxtLink to="/" class="rounded px-2 py-1 hover:bg-slate-100 dark:hover:bg-slate-800">{{ $t('shell.nav.dashboard') }}</NuxtLink>
        <NuxtLink
          v-if="auth.user?.accountRole === 'ADMIN'"
          to="/admin"
          class="rounded px-2 py-1 hover:bg-slate-100 dark:hover:bg-slate-800"
        >{{ $t('shell.nav.admin') }}</NuxtLink>
      </nav>

      <div class="flex items-center gap-2">
        <div ref="themeRef" class="relative">
          <button
            type="button"
            class="rounded p-2 text-slate-500 hover:bg-slate-100 hover:text-slate-900 dark:text-slate-400 dark:hover:bg-slate-800 dark:hover:text-slate-100"
            :aria-label="$t('common.theme.label')"
            @click="themeOpen = !themeOpen"
          >
            <ClientOnly>
              <component :is="colorMode.value === 'dark' ? Moon : Sun" class="h-4 w-4" />
              <template #fallback><Sun class="h-4 w-4" /></template>
            </ClientOnly>
          </button>
          <div
            v-if="themeOpen"
            class="absolute right-0 mt-2 w-40 overflow-hidden rounded-lg border border-slate-200 bg-white py-1 shadow-lg dark:border-slate-800 dark:bg-slate-900"
          >
            <button
              v-for="opt in themeOptions"
              :key="opt.value"
              type="button"
              class="flex w-full items-center justify-between px-3 py-1.5 text-left text-xs text-slate-700 hover:bg-slate-50 dark:text-slate-200 dark:hover:bg-slate-800/60"
              @click="onSelectTheme(opt.value)"
            >
              <span class="flex items-center gap-2">
                <component :is="opt.icon" class="h-3.5 w-3.5 text-slate-400 dark:text-slate-500" />
                {{ $t(opt.labelKey) }}
              </span>
              <Check v-if="colorMode.preference === opt.value" class="h-3.5 w-3.5 text-emerald-500 dark:text-emerald-400" />
            </button>
          </div>
        </div>

        <div ref="langRef" class="relative">
          <button
            type="button"
            class="flex items-center gap-1 rounded p-2 text-slate-500 hover:bg-slate-100 hover:text-slate-900 dark:text-slate-400 dark:hover:bg-slate-800 dark:hover:text-slate-100"
            :aria-label="$t('common.actions.language')"
            @click="langOpen = !langOpen"
          >
            <Globe class="h-4 w-4" />
            <span class="text-[11px] font-medium uppercase">{{ locale }}</span>
          </button>
          <div
            v-if="langOpen"
            class="absolute right-0 mt-2 w-36 overflow-hidden rounded-lg border border-slate-200 bg-white py-1 shadow-lg dark:border-slate-800 dark:bg-slate-900"
          >
            <button
              v-for="l in locales"
              :key="l.code"
              type="button"
              class="flex w-full items-center justify-between px-3 py-1.5 text-left text-xs text-slate-700 hover:bg-slate-50 dark:text-slate-200 dark:hover:bg-slate-800/60"
              @click="onSelectLocale(l.code as 'ko' | 'en')"
            >
              <span>{{ l.name }}</span>
              <Check v-if="l.code === locale" class="h-3.5 w-3.5 text-emerald-500 dark:text-emerald-400" />
            </button>
          </div>
        </div>

        <NuxtLink
          v-if="auth.isAuthenticated"
          to="/settings"
          class="rounded p-2 text-slate-500 hover:bg-slate-100 hover:text-slate-900 dark:text-slate-400 dark:hover:bg-slate-800 dark:hover:text-slate-100"
          :aria-label="$t('common.actions.settings')"
        >
          <Settings class="h-4 w-4" />
        </NuxtLink>

        <div ref="dropdownRef" class="relative">
          <button
            type="button"
            class="relative rounded p-2 hover:bg-slate-100 dark:hover:bg-slate-800"
            :aria-label="$t('shell.notifications.title')"
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
            class="absolute right-0 mt-2 w-80 overflow-hidden rounded-lg border border-slate-200 bg-white shadow-lg dark:border-slate-800 dark:bg-slate-900"
          >
            <div class="flex items-center justify-between border-b border-slate-100 px-3 py-2 dark:border-slate-800">
              <span class="text-xs font-medium text-slate-500 dark:text-slate-400">{{ $t('shell.notifications.title') }}</span>
              <button
                v-if="notifs.unreadCount > 0"
                type="button"
                class="text-xs font-medium text-emerald-600 hover:text-emerald-700 dark:text-emerald-400 dark:hover:text-emerald-300"
                @click="notifs.markAllRead()"
              >
                {{ $t('shell.notifications.markAllRead') }}
              </button>
            </div>
            <ul v-if="notifs.items.length > 0" class="max-h-80 overflow-y-auto divide-y divide-slate-100 dark:divide-slate-800">
              <li
                v-for="n in notifs.items"
                :key="n.id"
                :class="[
                  'cursor-pointer px-3 py-2 hover:bg-slate-50 dark:hover:bg-slate-800/60',
                  !n.read && 'bg-emerald-50/40 dark:bg-emerald-500/5',
                ]"
                @click="onClickNotif(n.id)"
              >
                <p v-if="n.title" class="truncate text-sm font-medium text-slate-800 dark:text-slate-100">{{ n.title }}</p>
                <p
                  :class="[
                    'line-clamp-2',
                    n.title ? 'mt-0.5 text-xs text-slate-600 dark:text-slate-300' : 'text-sm text-slate-700 dark:text-slate-200',
                  ]"
                >{{ n.message }}</p>
                <p class="mt-0.5 text-[11px] text-slate-400 dark:text-slate-500">
                  <span v-if="n.actorName">{{ n.actorName }} · </span>
                  <span v-if="n.projectName">{{ n.projectName }} · </span>
                  {{ timeAgo(n.createdAt) }}
                </p>
              </li>
            </ul>
            <p v-else class="px-3 py-6 text-center text-xs text-slate-400 dark:text-slate-500">{{ $t('shell.notifications.empty') }}</p>
          </div>
        </div>

        <div v-if="auth.user" class="flex items-center gap-1 border-l border-slate-200 pl-2 dark:border-slate-800">
          <button
            type="button"
            class="flex items-center gap-2 rounded px-1.5 py-1 hover:bg-slate-100 dark:hover:bg-slate-800"
            :aria-label="$t('shell.user.editProfile')"
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
              class="flex h-7 w-7 items-center justify-center rounded-full bg-slate-100 text-slate-400 dark:bg-slate-800 dark:text-slate-500"
            >
              <UserRound class="h-4 w-4" />
            </span>
            <span class="hidden text-left text-xs sm:block">
              <span class="block font-medium">{{ auth.user.name }}</span>
              <span class="block text-slate-500 dark:text-slate-400">{{ auth.user.role ?? '' }}</span>
            </span>
          </button>
          <button
            type="button"
            class="rounded p-2 text-slate-500 hover:bg-slate-100 hover:text-slate-900 dark:text-slate-400 dark:hover:bg-slate-800 dark:hover:text-slate-100"
            :aria-label="$t('common.actions.logout')"
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
