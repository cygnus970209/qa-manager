<script setup lang="ts">
import {
  ArrowUpDown, Bell, Check, ChevronDown, ClipboardList, Folder, GitBranch, Home, LayoutDashboard, LogOut,
  Monitor, Moon, PanelLeftClose, PanelLeftOpen, Pin, PinOff, Play, Plus, Search, Settings, ShieldCheck, Sun,
  UserRound, Users, X,
} from '@lucide/vue'
import NewProjectModal from '~/components/feature/NewProjectModal.vue'
import ReorderProjectModal from '~/components/feature/ReorderProjectModal.vue'
import type { Project } from '~/types/api'

/**
 * 앱 사이드바.
 * - mode 'desktop': 레이아웃 왼쪽에 고정. 접힘(56px 아이콘 스트립) / 펼침(256px 트리) 두 상태.
 * - mode 'drawer' : 모바일 오버레이. 항상 펼친 트리로 그리고 닫기 버튼을 보여준다.
 * 프로젝트 트리: 고정 프로젝트 → 구분선 → 나머지. 현재 프로젝트는 그 자리에서 펼쳐져 하위 메뉴가 나온다.
 */
const props = withDefaults(defineProps<{ mode?: 'desktop' | 'drawer' }>(), { mode: 'desktop' })

const sidebar = useSidebarStore()
const auth = useAuthStore()
const notifs = useNotificationsStore()
const projectsApi = useProjects()
const route = useRoute()
const router = useRouter()

const collapsed = computed(() => props.mode === 'desktop' && sidebar.collapsed)
const isAdmin = computed(() => auth.user?.accountRole === 'ADMIN')

/* ─── 프로젝트 목록 ─── */
const search = ref('')
const searchOpen = ref(false)
const searchInput = ref<HTMLInputElement | null>(null)
function toggleSearch() {
  searchOpen.value = !searchOpen.value
  search.value = ''
  if (searchOpen.value) nextTick(() => searchInput.value?.focus())
}
const filtered = computed(() => {
  const q = search.value.trim().toLowerCase()
  return q ? sidebar.projects.filter((p) => p.name.toLowerCase().includes(q)) : sidebar.projects
})
/** [고정, 나머지] — 템플릿에서 같은 행 마크업을 두 번 쓰지 않기 위해 묶는다 */
const groups = computed(() => [filtered.value.filter((p) => p.pinned), filtered.value.filter((p) => !p.pinned)])

/** 라우트가 프로젝트 화면이면 그 id, 아니면 페이지가 알려준 프로젝트(QA 상세·테스트 런) */
const routeProjectId = computed(() => (route.path.startsWith('/project/') ? Number(route.params.id) : null))
const currentProjectId = computed(() => routeProjectId.value ?? sidebar.activeProjectId)

/** 프로젝트의 새 알림 수 — 프로젝트를 열면 지워진다 (stores/sidebar.ts) */
function badge(p: Project) {
  return sidebar.badge(p.id)
}
function dotClass(p: Project) {
  if (p.status === 'active') return 'bg-emerald-400'
  if (p.status === 'paused') return 'bg-amber-500'
  return 'bg-slate-300 dark:bg-slate-600'
}

/* ─── 프로젝트 하위 메뉴 ─── */
type SubKey = 'overview' | 'cases' | 'flow' | 'runs'
const subItems: { key: SubKey; icon: unknown; label: string }[] = [
  { key: 'overview', icon: Home, label: 'shell.sidebar.menu.overview' },
  { key: 'cases', icon: ClipboardList, label: 'shell.sidebar.menu.testCases' },
  { key: 'flow', icon: GitBranch, label: 'shell.sidebar.menu.testFlow' },
  { key: 'runs', icon: Play, label: 'shell.sidebar.menu.testRuns' },
]
function subTo(pid: number, key: SubKey) {
  switch (key) {
    case 'overview': return `/project/${pid}`
    case 'cases': return `/project/${pid}?tab=tests`
    case 'flow': return `/project/${pid}?tab=tests&view=flow`
    case 'runs': return `/project/${pid}?tab=runs`
  }
}
const activeSub = computed<SubKey | null>(() => {
  if (route.path.startsWith('/run/')) return 'runs'
  if (!routeProjectId.value) return null
  const tab = route.query.tab
  if (tab === 'runs') return 'runs'
  if (tab === 'tests') return route.query.view === 'flow' ? 'flow' : 'cases'
  return 'overview'
})

/* ─── 새 프로젝트 ─── */
const projectModalOpen = ref(false)
async function onProjectCreated(p: Project) {
  projectModalOpen.value = false
  await sidebar.reload()
  router.push(`/project/${p.id}`)
}

/* ─── 프로젝트 순서 변경 (사용자별, 서버 저장) ─── */
const reorderOpen = ref(false)
function onReordered(list: Project[]) {
  sidebar.projects = list
}

/* ─── 프로젝트 우클릭 메뉴 (고정/해제) ─── */
const ctx = ref<{ x: number; y: number; project: Project } | null>(null)
const ctxRef = ref<HTMLElement | null>(null)
function openCtx(e: MouseEvent, p: Project) {
  // 메뉴가 화면 밖으로 나가지 않게 오른쪽/아래 여백을 남긴다
  const x = Math.min(e.clientX, window.innerWidth - 200)
  const y = Math.min(e.clientY, window.innerHeight - 60)
  ctx.value = { x, y, project: p }
}
const pinBusy = ref(false)
async function togglePin(p: Project) {
  ctx.value = null
  if (pinBusy.value) return
  pinBusy.value = true
  try {
    await projectsApi.togglePin(p.id)
    await sidebar.reload()
  } catch (e) {
    console.warn('[sidebar] 고정 변경 실패:', e)
  } finally {
    pinBusy.value = false
  }
}
function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape') ctx.value = null
}
onMounted(() => window.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))

/* ─── 접힘 상태의 프로젝트 팝오버 ─── */
const popoverOpen = ref(false)
const popoverRef = ref<HTMLElement | null>(null)

/* ─── 테마 ─── */
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

function onDocClick(e: MouseEvent) {
  const t = e.target as Node
  if (themeRef.value && !themeRef.value.contains(t)) themeOpen.value = false
  if (popoverRef.value && !popoverRef.value.contains(t)) popoverOpen.value = false
  if (ctxRef.value && !ctxRef.value.contains(t)) ctx.value = null
}
onMounted(() => document.addEventListener('mousedown', onDocClick))
onBeforeUnmount(() => document.removeEventListener('mousedown', onDocClick))

// 화면이 바뀌면 팝오버/드로어/우클릭 메뉴를 닫는다
watch(() => route.fullPath, () => {
  popoverOpen.value = false
  sidebar.mobileOpen = false
  ctx.value = null
})

async function onLogout() {
  await auth.logout()
  notifs.disconnect()
  sidebar.reset()
  router.push('/auth/login')
}

/* ─── 클래스 ─── */
const menuBase = 'flex h-9 items-center gap-2.5 rounded-md px-2.5 text-sm transition-colors'
const menuIdle = 'text-slate-700 hover:bg-slate-100 dark:text-slate-200 dark:hover:bg-slate-800'
const menuActive = 'bg-emerald-50 font-medium text-emerald-700 dark:bg-emerald-500/10 dark:text-emerald-400'
const rowBase = 'flex h-8 items-center gap-2.5 rounded-md px-2.5 text-sm transition-colors hover:bg-slate-100 dark:hover:bg-slate-800'
const subBase = 'ml-[18px] flex h-8 items-center gap-2.5 rounded-md px-2.5 text-sm transition-colors'
const stripBase = 'relative flex h-10 w-10 items-center justify-center rounded-md transition-colors'
const stripIdle = 'text-slate-500 hover:bg-slate-100 hover:text-slate-800 dark:text-slate-400 dark:hover:bg-slate-800 dark:hover:text-slate-100'
const stripActive = 'bg-emerald-50 text-emerald-600 dark:bg-emerald-500/10 dark:text-emerald-400'
const iconBtn = 'flex h-7 w-7 items-center justify-center rounded text-slate-400 hover:bg-slate-100 hover:text-slate-700 dark:text-slate-500 dark:hover:bg-slate-800 dark:hover:text-slate-200'
</script>

<template>
  <!-- ───────── 접힘: 아이콘 스트립 ───────── -->
  <aside
    v-if="collapsed"
    class="flex h-full w-14 shrink-0 flex-col items-center border-r border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-900"
  >
    <NuxtLink to="/" class="flex h-[53px] w-full shrink-0 items-center justify-center border-b border-slate-200 dark:border-slate-800" :title="$t('common.appName')">
      <ShieldCheck class="h-5 w-5 text-emerald-600 dark:text-emerald-400" />
    </NuxtLink>
    <div class="flex min-h-0 flex-1 flex-col items-center gap-1 py-3">
      <button type="button" :class="[stripBase, stripIdle, 'h-8']" :title="$t('shell.sidebar.expand')" @click="sidebar.toggle()">
        <PanelLeftOpen class="h-4 w-4" />
      </button>
      <div class="my-1 h-px w-6 bg-slate-200 dark:bg-slate-800" />
      <NuxtLink to="/" :class="[stripBase, route.path === '/' ? stripActive : stripIdle]" :title="$t('shell.nav.dashboard')">
        <LayoutDashboard class="h-[18px] w-[18px]" />
      </NuxtLink>
      <NuxtLink to="/notifications" :class="[stripBase, route.path.startsWith('/notifications') ? stripActive : stripIdle]" :title="$t('shell.nav.notifications')">
        <Bell class="h-[18px] w-[18px]" />
        <span
          v-if="notifs.unreadCount > 0"
          class="absolute right-1 top-1 inline-flex h-4 min-w-[16px] items-center justify-center rounded-full bg-rose-500 px-1 text-[10px] font-medium text-white"
        >{{ notifs.unreadCount }}</span>
      </NuxtLink>
      <NuxtLink v-if="isAdmin" to="/admin" :class="[stripBase, route.path.startsWith('/admin') ? stripActive : stripIdle]" :title="$t('shell.nav.admin')">
        <Users class="h-[18px] w-[18px]" />
      </NuxtLink>
      <div class="my-1 h-px w-6 bg-slate-200 dark:bg-slate-800" />
      <div ref="popoverRef" class="relative">
        <button
          type="button"
          :class="[stripBase, currentProjectId != null ? stripActive : stripIdle]"
          :title="$t('shell.sidebar.projects')"
          @click="popoverOpen = !popoverOpen"
        >
          <Folder class="h-[18px] w-[18px]" />
        </button>
        <div
          v-if="popoverOpen"
          class="absolute left-full top-0 z-40 ml-2 w-64 overflow-hidden rounded-lg border border-slate-200 bg-white py-1 shadow-lg dark:border-slate-800 dark:bg-slate-900"
        >
          <p class="px-3 py-1.5 text-[11px] font-semibold uppercase tracking-wider text-slate-400 dark:text-slate-500">{{ $t('shell.sidebar.projects') }}</p>
          <div class="max-h-80 overflow-y-auto">
            <template v-for="(group, gi) in groups" :key="gi">
              <div v-if="gi === 1 && groups[0]!.length > 0 && group.length > 0" class="mx-3 my-1 h-px bg-slate-100 dark:bg-slate-800" />
              <NuxtLink
                v-for="p in group"
                :key="p.id"
                :to="`/project/${p.id}`"
                :class="[
                  'flex h-8 items-center gap-2.5 px-3 text-sm hover:bg-slate-50 dark:hover:bg-slate-800/60',
                  p.id === currentProjectId ? 'font-semibold text-slate-800 dark:text-slate-100' : p.status === 'completed' ? 'text-slate-400 dark:text-slate-500' : 'text-slate-700 dark:text-slate-200',
                ]"
              >
                <span :class="['h-2 w-2 shrink-0 rounded-full', dotClass(p)]" />
                <span class="min-w-0 flex-1 truncate">{{ p.name }}</span>
                <span
                  v-if="badge(p) > 0"
                  class="inline-flex h-[18px] min-w-[18px] items-center justify-center rounded-full bg-rose-50 px-1.5 text-[11px] font-medium text-rose-600 dark:bg-rose-500/10 dark:text-rose-400"
                >{{ badge(p) }}</span>
              </NuxtLink>
            </template>
            <p v-if="sidebar.loaded && sidebar.projects.length === 0" class="px-3 py-3 text-xs text-slate-400 dark:text-slate-500">{{ $t('shell.sidebar.noProjects') }}</p>
          </div>
          <button
            type="button"
            class="flex h-8 w-full items-center gap-2.5 border-t border-slate-100 px-3 text-sm text-slate-500 hover:bg-slate-50 dark:border-slate-800 dark:text-slate-400 dark:hover:bg-slate-800/60"
            @click="popoverOpen = false; projectModalOpen = true"
          >
            <Plus class="h-4 w-4" /> {{ $t('shell.sidebar.newProject') }}
          </button>
        </div>
      </div>
    </div>
    <NuxtLink
      to="/settings/account"
      class="flex h-14 w-full shrink-0 items-center justify-center border-t border-slate-200 bg-slate-50 dark:border-slate-800 dark:bg-slate-900/60"
      :title="auth.user?.name ?? ''"
    >
      <img v-if="auth.user?.avatarUrl" :src="auth.user.avatarUrl" :alt="auth.user.name" class="h-7 w-7 rounded-full bg-slate-100 object-cover" />
      <span v-else class="flex h-7 w-7 items-center justify-center rounded-full bg-slate-100 text-slate-400 dark:bg-slate-800 dark:text-slate-500"><UserRound class="h-4 w-4" /></span>
    </NuxtLink>
    <NewProjectModal :open="projectModalOpen" @close="projectModalOpen = false" @created="onProjectCreated" />
  </aside>

  <!-- ───────── 펼침: 트리 ───────── -->
  <aside
    v-else
    class="flex h-full w-64 shrink-0 flex-col border-r border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-900"
  >
    <div class="flex h-[53px] shrink-0 items-center gap-2 border-b border-slate-200 pl-4 pr-3 dark:border-slate-800">
      <NuxtLink to="/" class="flex min-w-0 flex-1 items-center gap-2">
        <ShieldCheck class="h-5 w-5 shrink-0 text-emerald-600 dark:text-emerald-400" />
        <span class="truncate text-sm font-semibold tracking-tight">{{ $t('common.appName') }}</span>
      </NuxtLink>
      <button v-if="mode === 'drawer'" type="button" :class="iconBtn" :title="$t('shell.sidebar.closeMenu')" @click="sidebar.mobileOpen = false">
        <X class="h-4 w-4" />
      </button>
      <button v-else type="button" :class="iconBtn" :title="$t('shell.sidebar.collapse')" @click="sidebar.toggle()">
        <PanelLeftClose class="h-4 w-4" />
      </button>
    </div>

    <nav class="flex min-h-0 flex-1 flex-col gap-0.5 overflow-y-auto px-2 py-3">
      <NuxtLink to="/" :class="[menuBase, route.path === '/' ? menuActive : menuIdle]">
        <LayoutDashboard :class="['h-4 w-4 shrink-0', route.path === '/' ? 'text-emerald-600 dark:text-emerald-400' : 'text-slate-400 dark:text-slate-500']" />
        <span class="flex-1 truncate">{{ $t('shell.nav.dashboard') }}</span>
      </NuxtLink>
      <NuxtLink to="/notifications" :class="[menuBase, route.path.startsWith('/notifications') ? menuActive : menuIdle]">
        <Bell :class="['h-4 w-4 shrink-0', route.path.startsWith('/notifications') ? 'text-emerald-600 dark:text-emerald-400' : 'text-slate-400 dark:text-slate-500']" />
        <span class="flex-1 truncate">{{ $t('shell.nav.notifications') }}</span>
        <span
          v-if="notifs.unreadCount > 0"
          class="inline-flex h-[18px] min-w-[18px] items-center justify-center rounded-full bg-rose-500 px-1.5 text-[11px] font-medium text-white"
        >{{ notifs.unreadCount }}</span>
      </NuxtLink>
      <NuxtLink v-if="isAdmin" to="/admin" :class="[menuBase, route.path.startsWith('/admin') ? menuActive : menuIdle]">
        <Users :class="['h-4 w-4 shrink-0', route.path.startsWith('/admin') ? 'text-emerald-600 dark:text-emerald-400' : 'text-slate-400 dark:text-slate-500']" />
        <span class="flex-1 truncate">{{ $t('shell.nav.admin') }}</span>
      </NuxtLink>

      <div class="mb-1 mt-3 flex items-center justify-between px-2.5">
        <span class="text-[11px] font-semibold uppercase tracking-wider text-slate-400 dark:text-slate-500">{{ $t('shell.sidebar.projects') }}</span>
        <span class="flex items-center gap-0.5">
          <button type="button" :class="[iconBtn, 'h-5 w-5', searchOpen && 'text-slate-700 dark:text-slate-200']" :title="$t('shell.sidebar.searchProjects')" @click="toggleSearch">
            <Search class="h-3.5 w-3.5" />
          </button>
          <button type="button" :class="[iconBtn, 'h-5 w-5']" :title="$t('shell.sidebar.reorder')" @click="reorderOpen = true">
            <ArrowUpDown class="h-3.5 w-3.5" />
          </button>
          <button type="button" :class="[iconBtn, 'h-5 w-5']" :title="$t('shell.sidebar.newProject')" @click="projectModalOpen = true">
            <Plus class="h-3.5 w-3.5" />
          </button>
        </span>
      </div>
      <div v-if="searchOpen" class="px-1 pb-1">
        <input
          ref="searchInput"
          v-model="search"
          type="text"
          :placeholder="$t('shell.sidebar.searchPlaceholder')"
          class="w-full rounded-md border border-slate-200 bg-white px-2 py-1 text-xs focus:border-emerald-300 focus:outline-none focus:ring-2 focus:ring-emerald-200 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100 dark:placeholder-slate-500 dark:focus:ring-emerald-500/20"
          @keydown.escape="toggleSearch"
        />
      </div>

      <template v-for="(group, gi) in groups" :key="gi">
        <div v-if="gi === 1 && groups[0]!.length > 0 && group.length > 0" class="mx-2.5 my-1.5 h-px bg-slate-100 dark:bg-slate-800" />
        <template v-for="p in group" :key="p.id">
          <NuxtLink
            :to="`/project/${p.id}`"
            :class="[
              rowBase,
              p.id === currentProjectId
                ? 'font-semibold text-slate-800 dark:text-slate-100'
                : p.status === 'completed' ? 'text-slate-400 dark:text-slate-500' : 'text-slate-700 dark:text-slate-200',
            ]"
            @contextmenu.prevent="openCtx($event, p)"
          >
            <span :class="['h-2 w-2 shrink-0 rounded-full', dotClass(p)]" />
            <span class="min-w-0 flex-1 truncate">{{ p.name }}</span>
            <ChevronDown v-if="p.id === currentProjectId" class="h-3.5 w-3.5 shrink-0 text-slate-400 dark:text-slate-500" />
            <span
              v-else-if="badge(p) > 0"
              class="inline-flex h-[18px] min-w-[18px] items-center justify-center rounded-full bg-rose-50 px-1.5 text-[11px] font-medium text-rose-600 dark:bg-rose-500/10 dark:text-rose-400"
              :title="$t('shell.sidebar.newNotifications', { n: badge(p) })"
            >{{ badge(p) }}</span>
          </NuxtLink>
          <div v-if="p.id === currentProjectId" class="mb-1.5 ml-[23px] mt-0.5 flex flex-col gap-0.5 border-l border-slate-200 dark:border-slate-800">
            <NuxtLink
              v-for="s in subItems"
              :key="s.key"
              :to="subTo(p.id, s.key)"
              :class="[subBase, activeSub === s.key ? menuActive : 'text-slate-600 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800']"
            >
              <component :is="s.icon" :class="['h-[15px] w-[15px] shrink-0', activeSub === s.key ? 'text-emerald-600 dark:text-emerald-400' : 'text-slate-400 dark:text-slate-500']" />
              <span class="truncate">{{ $t(s.label) }}</span>
            </NuxtLink>
          </div>
        </template>
      </template>
      <p v-if="sidebar.loaded && filtered.length === 0" class="px-2.5 py-2 text-xs text-slate-400 dark:text-slate-500">
        {{ sidebar.projects.length === 0 ? $t('shell.sidebar.noProjects') : $t('shell.sidebar.noMatch') }}
      </p>
    </nav>

    <footer class="flex h-14 shrink-0 items-center gap-1 border-t border-slate-200 bg-slate-50 px-2 dark:border-slate-800 dark:bg-slate-900/60">
      <NuxtLink
        to="/settings/account"
        class="flex min-w-0 flex-1 items-center gap-2 rounded-md px-1.5 py-1 hover:bg-slate-100 dark:hover:bg-slate-800"
        :title="$t('shell.user.editProfile')"
      >
        <img v-if="auth.user?.avatarUrl" :src="auth.user.avatarUrl" :alt="auth.user.name" class="h-7 w-7 shrink-0 rounded-full bg-slate-100 object-cover" />
        <span v-else class="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-slate-100 text-slate-400 dark:bg-slate-800 dark:text-slate-500"><UserRound class="h-4 w-4" /></span>
        <span class="min-w-0 text-xs">
          <span class="block truncate font-medium">{{ auth.user?.name ?? '' }}</span>
          <span class="block truncate text-slate-500 dark:text-slate-400">{{ auth.user?.role ?? '' }}</span>
        </span>
      </NuxtLink>
      <div ref="themeRef" class="relative">
        <button type="button" :class="iconBtn" :aria-label="$t('common.theme.label')" @click="themeOpen = !themeOpen">
          <ClientOnly>
            <component :is="colorMode.value === 'dark' ? Moon : Sun" class="h-4 w-4" />
            <template #fallback><Sun class="h-4 w-4" /></template>
          </ClientOnly>
        </button>
        <div
          v-if="themeOpen"
          class="absolute bottom-full right-0 mb-2 w-40 overflow-hidden rounded-lg border border-slate-200 bg-white py-1 shadow-lg dark:border-slate-800 dark:bg-slate-900"
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
      <NuxtLink to="/settings" :class="iconBtn" :aria-label="$t('common.actions.settings')">
        <Settings class="h-4 w-4" />
      </NuxtLink>
      <button type="button" :class="iconBtn" :aria-label="$t('common.actions.logout')" @click="onLogout">
        <LogOut class="h-4 w-4" />
      </button>
    </footer>
    <NewProjectModal :open="projectModalOpen" @close="projectModalOpen = false" @created="onProjectCreated" />
    <ReorderProjectModal :open="reorderOpen" :projects="sidebar.projects" @close="reorderOpen = false" @reordered="onReordered" />

    <!-- 프로젝트 우클릭 메뉴 -->
    <Teleport to="body">
      <div
        v-if="ctx"
        ref="ctxRef"
        class="fixed z-50 w-44 overflow-hidden rounded-lg border border-slate-200 bg-white py-1 shadow-lg dark:border-slate-800 dark:bg-slate-900"
        :style="{ left: `${ctx.x}px`, top: `${ctx.y}px` }"
        role="menu"
      >
        <p class="truncate px-3 py-1.5 text-[11px] text-slate-400 dark:text-slate-500">{{ ctx.project.name }}</p>
        <button
          type="button"
          role="menuitem"
          class="flex w-full items-center gap-2 px-3 py-1.5 text-left text-xs text-slate-700 hover:bg-slate-50 dark:text-slate-200 dark:hover:bg-slate-800/60"
          :disabled="pinBusy"
          @click="togglePin(ctx.project)"
        >
          <component :is="ctx.project.pinned ? PinOff : Pin" class="h-3.5 w-3.5 text-slate-400 dark:text-slate-500" />
          {{ ctx.project.pinned ? $t('shell.sidebar.unpin') : $t('shell.sidebar.pin') }}
        </button>
      </div>
    </Teleport>
  </aside>
</template>
