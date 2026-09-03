<script setup lang="ts">
import { Bell, ExternalLink, Inbox } from '@lucide/vue'
import QAInfoPanel from '~/components/feature/QAInfoPanel.vue'
import QACommentSection from '~/components/feature/QACommentSection.vue'
import { timeAgo } from '~/utils/format'
import type { Member, Notification, Project, ProjectUpdate, QaComment, QaItem } from '~/types/api'

/**
 * 알림 페이지 — Teams 활동 피드처럼 왼쪽 목록, 오른쪽에 고른 알림의 QA 내용을 그 자리에서 보여준다.
 * 모바일(md 미만)에서는 목록만 보이고, 알림을 누르면 QA 상세 페이지로 이동한다.
 */
definePageMeta({ fullBleed: true })

const notifs = useNotificationsStore()
const sidebar = useSidebarStore()
const qaApi = useQa()
const membersApi = useMembers()
const updatesApi = useUpdates()
const projectsApi = useProjects()
const route = useRoute()
const router = useRouter()
const { t } = useI18n()
const isDesktop = useMediaQuery('(min-width: 768px)')

type Filter = 'all' | 'unread' | 'mentions'
const filter = ref<Filter>('all')
const filters: { key: Filter; label: string }[] = [
  { key: 'all', label: 'shell.notifications.filterAll' },
  { key: 'unread', label: 'shell.notifications.filterUnread' },
  { key: 'mentions', label: 'shell.notifications.filterMentions' },
]
const list = computed(() => notifs.items.filter((n) => {
  if (filter.value === 'unread') return !n.read
  if (filter.value === 'mentions') return n.type === 'mention'
  return true
}))

/* ─── 선택 + 오른쪽 QA 내용 ─── */
const selectedId = ref<number | null>(typeof route.query.id === 'string' ? Number(route.query.id) : null)
const selected = computed(() => notifs.items.find((n) => n.id === selectedId.value) ?? null)

const item = ref<QaItem | null>(null)
const comments = ref<QaComment[]>([])
const members = ref<Member[]>([])
const allUpdates = ref<ProjectUpdate[]>([])
const allItems = ref<QaItem[]>([])
const projects = ref<Project[]>([])
const loading = ref(false)
const error = ref<string | null>(null)

const updateOf = computed(() => (item.value ? allUpdates.value.find((u) => u.id === item.value!.updateId) ?? null : null))
const projectOf = computed(() => (updateOf.value ? projects.value.find((p) => p.id === updateOf.value!.projectId) ?? null : null))
/** 현재 QA 가 옮겨갈 수 있는 업데이트(같은 프로젝트) — QAInfoPanel 용 */
const movableUpdates = computed(() => {
  const pid = updateOf.value?.projectId
  const arr = pid == null ? allUpdates.value : allUpdates.value.filter((u) => u.projectId === pid)
  return [...arr].sort((a, b) => a.version.localeCompare(b.version, undefined, { numeric: true }))
})

let loadSeq = 0
async function loadQa(qaId: number | null) {
  item.value = null
  comments.value = []
  error.value = null
  sidebar.activeProjectId = null
  if (!qaId) return
  const seq = ++loadSeq
  loading.value = true
  try {
    const [q, c] = await Promise.all([qaApi.get(qaId), qaApi.listComments(qaId)])
    if (seq !== loadSeq) return
    item.value = q
    comments.value = c
    // 보조 데이터는 최초 1회만 (멤버 선택·업데이트 이동·#태그 자동완성용)
    if (members.value.length === 0) members.value = await membersApi.list()
    if (allUpdates.value.length === 0) allUpdates.value = await updatesApi.listAll()
    if (projects.value.length === 0) projects.value = await projectsApi.list()
    if (allItems.value.length === 0) allItems.value = await qaApi.list()
    if (seq === loadSeq) sidebar.activeProjectId = updateOf.value?.projectId ?? null
  } catch (e: any) {
    if (seq === loadSeq) error.value = e?.data?.message ?? t('shell.notifications.loadFailed')
  } finally {
    if (seq === loadSeq) loading.value = false
  }
}

async function select(n: Notification) {
  if (!isDesktop.value) {
    // 모바일: 분할 화면 대신 상세 페이지로
    await notifs.openNotification(n.id)
    return
  }
  selectedId.value = n.id
  router.replace({ query: { ...route.query, id: String(n.id) } })
  if (!n.read) notifs.markRead(n.id).catch(() => { /* 읽음 실패는 무시 */ })
  await loadQa(n.qaItemId)
}

// 직접 URL 로 들어온 경우: 알림 목록이 로드되면 쿼리의 알림을 연다
let opened = false
watch(() => notifs.items.length, (len) => {
  if (opened || len === 0 || selectedId.value == null) return
  const n = notifs.items.find((x) => x.id === selectedId.value)
  if (!n) return
  opened = true
  if (!n.read) notifs.markRead(n.id).catch(() => { /* ignore */ })
  loadQa(n.qaItemId)
}, { immediate: true })

onBeforeUnmount(() => { sidebar.activeProjectId = null })

function onUpdated(updated: QaItem) {
  item.value = updated
  allItems.value = allItems.value.map((q) => (q.id === updated.id ? updated : q))
}
function onRemoved() {
  item.value = null
  selectedId.value = null
  router.replace({ query: { ...route.query, id: undefined } })
}

function typeLabel(n: Notification) {
  return n.projectName ? `${n.actorName ?? ''}${n.actorName ? ' · ' : ''}${n.projectName}` : (n.actorName ?? '')
}
</script>

<template>
  <div class="flex h-[calc(100vh-53px)] min-h-0 flex-1 md:h-screen">
    <!-- 목록 -->
    <section class="flex w-full shrink-0 flex-col border-r border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-900 md:w-[400px]">
      <div class="flex h-[53px] shrink-0 items-center justify-between border-b border-slate-200 px-4 dark:border-slate-800">
        <span class="flex items-center gap-2">
          <span class="text-base font-semibold text-slate-800 dark:text-slate-100">{{ $t('shell.notifications.title') }}</span>
          <span
            v-if="notifs.unreadCount > 0"
            class="inline-flex h-[18px] min-w-[18px] items-center justify-center rounded-full bg-rose-500 px-1.5 text-[11px] font-medium text-white"
          >{{ notifs.unreadCount }}</span>
        </span>
        <button
          v-if="notifs.unreadCount > 0"
          type="button"
          class="text-xs font-medium text-emerald-600 hover:text-emerald-700 dark:text-emerald-400 dark:hover:text-emerald-300"
          @click="notifs.markAllRead()"
        >{{ $t('shell.notifications.markAllRead') }}</button>
      </div>
      <div class="flex shrink-0 items-center gap-2 border-b border-slate-100 px-4 py-2 dark:border-slate-800">
        <div class="inline-flex rounded-lg bg-slate-100 p-0.5 dark:bg-slate-800">
          <button
            v-for="f in filters"
            :key="f.key"
            type="button"
            :class="[
              'rounded-md px-3 py-1 text-xs font-medium transition-colors',
              filter === f.key ? 'bg-white text-slate-800 shadow-sm dark:bg-slate-900 dark:text-slate-100' : 'text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-200',
            ]"
            @click="filter = f.key"
          >{{ $t(f.label) }}</button>
        </div>
      </div>
      <div class="min-h-0 flex-1 overflow-y-auto">
        <button
          v-for="n in list"
          :key="n.id"
          type="button"
          :class="[
            'flex w-full flex-col gap-1 border-b border-slate-100 px-4 py-3 text-left transition-colors dark:border-slate-800',
            n.id === selectedId
              ? 'bg-emerald-50/70 ring-1 ring-inset ring-emerald-200 dark:bg-emerald-500/10 dark:ring-emerald-500/20'
              : !n.read ? 'bg-emerald-50/40 hover:bg-emerald-50/70 dark:bg-emerald-500/5 dark:hover:bg-emerald-500/10' : 'hover:bg-slate-50 dark:hover:bg-slate-800/60',
          ]"
          @click="select(n)"
        >
          <span class="flex w-full items-center gap-2">
            <span :class="['h-1.5 w-1.5 shrink-0 rounded-full', n.read ? 'bg-transparent' : 'bg-emerald-500']" />
            <span :class="['min-w-0 flex-1 truncate text-sm text-slate-800 dark:text-slate-100', n.read ? 'font-medium' : 'font-semibold']">{{ n.title ?? $t('common.appName') }}</span>
            <span class="shrink-0 text-[11px] text-slate-400 dark:text-slate-500">{{ timeAgo(n.createdAt) }}</span>
          </span>
          <span :class="['line-clamp-2 pl-3.5 text-xs', n.read ? 'text-slate-500 dark:text-slate-400' : 'text-slate-700 dark:text-slate-200']">{{ n.message }}</span>
          <span class="pl-3.5 text-[11px] text-slate-400 dark:text-slate-500">{{ typeLabel(n) }}</span>
        </button>
        <p v-if="list.length === 0" class="px-4 py-12 text-center text-xs text-slate-400 dark:text-slate-500">
          <Inbox class="mx-auto mb-2 h-6 w-6 text-slate-300 dark:text-slate-600" />
          {{ notifs.items.length === 0 ? $t('shell.notifications.empty') : $t('shell.notifications.noMatch') }}
        </p>
      </div>
    </section>

    <!-- 오른쪽: 고른 알림의 QA -->
    <main class="hidden min-w-0 flex-1 overflow-y-auto p-6 md:block">
      <div v-if="!selected" class="flex h-full flex-col items-center justify-center text-center">
        <Bell class="mb-3 h-8 w-8 text-slate-300 dark:text-slate-600" />
        <p class="text-sm text-slate-400 dark:text-slate-500">{{ $t('shell.notifications.selectHint') }}</p>
      </div>
      <template v-else>
        <div class="mb-3 flex items-center justify-between gap-3">
          <span class="min-w-0 truncate text-xs text-slate-500 dark:text-slate-400">
            <template v-if="projectOf">{{ projectOf.name }}</template>
            <template v-if="updateOf"> · {{ updateOf.version }} {{ updateOf.title }}</template>
          </span>
          <NuxtLink
            v-if="selected.qaItemId"
            :to="`/qa/${selected.qaItemId}`"
            class="inline-flex shrink-0 items-center gap-1 rounded-md border border-slate-200 bg-white px-2.5 py-1 text-xs font-medium text-slate-600 hover:bg-slate-50 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-300 dark:hover:bg-slate-800/60"
          >
            {{ $t('shell.notifications.openDetail') }} <ExternalLink class="h-3.5 w-3.5" />
          </NuxtLink>
        </div>

        <div v-if="!selected.qaItemId" class="rounded-xl border border-slate-200 bg-white p-6 text-center dark:border-slate-800 dark:bg-slate-900">
          <p class="text-sm text-slate-500 dark:text-slate-400">{{ $t('shell.notifications.noQa') }}</p>
          <NuxtLink
            v-if="selected.projectId"
            :to="`/project/${selected.projectId}`"
            class="mt-3 inline-flex items-center gap-1 text-xs font-medium text-emerald-600 hover:text-emerald-700 dark:text-emerald-400"
          >{{ $t('shell.notifications.openProject') }} →</NuxtLink>
        </div>
        <div v-else-if="loading" class="space-y-6">
          <div class="animate-pulse space-y-4 rounded-xl border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-900">
            <div class="h-5 w-3/4 rounded bg-slate-200 dark:bg-slate-800" />
            <div class="flex gap-2"><div class="h-5 w-16 rounded-full bg-slate-100 dark:bg-slate-800/60" /><div class="h-5 w-14 rounded-full bg-slate-100 dark:bg-slate-800/60" /></div>
            <div class="space-y-2"><div class="h-3 w-full rounded bg-slate-100 dark:bg-slate-800/60" /><div class="h-3 w-5/6 rounded bg-slate-100 dark:bg-slate-800/60" /></div>
          </div>
        </div>
        <div v-else-if="error" class="rounded-md bg-red-50 px-4 py-3 text-sm text-red-700 dark:bg-red-500/10 dark:text-red-400">{{ error }}</div>
        <div v-else-if="item" class="space-y-6">
          <QAInfoPanel :item="item" :members="members" :updates="movableUpdates" :qa-items="allItems" @updated="onUpdated" @removed="onRemoved" />
          <QACommentSection
            :qa-item-id="item.id"
            :comments="comments"
            :members="members"
            :qa-items="allItems"
            @refreshed="comments = $event"
          />
        </div>
      </template>
    </main>
  </div>
</template>
