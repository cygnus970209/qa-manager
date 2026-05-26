<script setup lang="ts">
import { ArrowLeft, ChevronLeft, ChevronRight } from '@lucide/vue'
import QAInfoPanel from '~/components/feature/QAInfoPanel.vue'
import QACommentSection from '~/components/feature/QACommentSection.vue'
import QAHistoryList from '~/components/feature/QAHistoryList.vue'
import type { Member, QaComment, QaHistoryEntry, QaItem } from '~/types/api'

const route = useRoute()
const router = useRouter()
const qaId = computed(() => Number(route.params.id))

const qaApi = useQa()
const membersApi = useMembers()

const item = ref<QaItem | null>(null)
const history = ref<QaHistoryEntry[]>([])
const comments = ref<QaComment[]>([])
const members = ref<Member[]>([])
const loading = ref(true)
const error = ref<string | null>(null)

async function load() {
  loading.value = true
  error.value = null
  try {
    item.value = await qaApi.get(qaId.value)
    history.value = await qaApi.history(qaId.value)
    comments.value = await qaApi.listComments(qaId.value)
    members.value = await membersApi.list()
  } catch (e: any) {
    error.value = e?.data?.message ?? 'QA 항목을 불러올 수 없습니다.'
  } finally {
    loading.value = false
  }
}
if (import.meta.client) onMounted(load)
watch(qaId, () => { if (import.meta.client) load() })

async function onUpdated(next: QaItem) {
  item.value = next
  history.value = await qaApi.history(qaId.value)
}

function onRemoved() {
  router.push('/')
}

/* ─── 이전/다음 게시글 (필터/정렬 컨텍스트 유지) ───
 * 리스트 페이지가 sessionStorage 에 저장한 'qa:nav:list' 의 ID 순서를 기준으로
 * 현재 ID 의 앞/뒤 항목으로 이동한다. 컨텍스트가 없으면 버튼 자체를 숨긴다.
 */
const navIds = computed<number[]>(() => {
  if (!import.meta.client) return []
  const raw = sessionStorage.getItem('qa:nav:list')
  if (!raw) return []
  try {
    const arr = JSON.parse(raw)
    return Array.isArray(arr) ? arr.filter((n: unknown) => typeof n === 'number') : []
  } catch { return [] }
})
const currentIdx = computed(() => navIds.value.indexOf(qaId.value))
const hasPrev = computed(() => currentIdx.value > 0)
const hasNext = computed(() => currentIdx.value >= 0 && currentIdx.value < navIds.value.length - 1)

function goPrev() {
  if (!hasPrev.value) return
  router.push(`/qa/${navIds.value[currentIdx.value - 1]}`)
}
function goNext() {
  if (!hasNext.value) return
  router.push(`/qa/${navIds.value[currentIdx.value + 1]}`)
}
</script>

<template>
  <section>
    <div class="mb-3 flex items-center justify-between">
      <button class="inline-flex items-center gap-1 text-xs text-slate-500 hover:text-slate-900" type="button" @click="router.back()">
        <ArrowLeft class="h-3.5 w-3.5" /> 뒤로
      </button>
      <div v-if="navIds.length > 0 && currentIdx >= 0" class="flex items-center gap-1 text-xs">
        <span class="mr-1 text-slate-400 tabular-nums">{{ currentIdx + 1 }} / {{ navIds.length }}</span>
        <button
          type="button"
          :disabled="!hasPrev"
          class="inline-flex items-center gap-1 rounded-md border border-slate-200 px-2 py-1 text-slate-600 hover:bg-slate-50 disabled:opacity-40"
          @click="goPrev"
        >
          <ChevronLeft class="h-3.5 w-3.5" /> 이전
        </button>
        <button
          type="button"
          :disabled="!hasNext"
          class="inline-flex items-center gap-1 rounded-md border border-slate-200 px-2 py-1 text-slate-600 hover:bg-slate-50 disabled:opacity-40"
          @click="goNext"
        >
          다음 <ChevronRight class="h-3.5 w-3.5" />
        </button>
      </div>
    </div>

    <div v-if="loading" class="grid grid-cols-1 gap-6 lg:grid-cols-3">
      <div class="space-y-6 lg:col-span-2">
        <!-- QA Info Panel skeleton -->
        <div class="animate-pulse space-y-4 rounded-xl border border-slate-200 bg-white p-5">
          <div class="flex items-start justify-between">
            <div class="flex-1 space-y-2">
              <div class="h-5 w-3/4 rounded bg-slate-200" />
              <div class="flex gap-2">
                <div class="h-5 w-16 rounded-full bg-slate-100" />
                <div class="h-5 w-14 rounded-full bg-slate-100" />
              </div>
            </div>
            <div class="flex gap-2">
              <div class="h-7 w-7 rounded-md bg-slate-100" />
              <div class="h-7 w-7 rounded-md bg-slate-100" />
            </div>
          </div>
          <div class="space-y-2">
            <div class="h-3 w-full rounded bg-slate-100" />
            <div class="h-3 w-5/6 rounded bg-slate-100" />
            <div class="h-3 w-2/3 rounded bg-slate-100" />
          </div>
          <div class="grid grid-cols-2 gap-3 pt-2">
            <div class="h-12 rounded-lg bg-slate-100" />
            <div class="h-12 rounded-lg bg-slate-100" />
          </div>
        </div>
        <!-- Comment skeleton -->
        <div class="overflow-hidden rounded-xl border border-slate-200 bg-white">
          <div class="border-b border-slate-100 p-4 md:p-5">
            <div class="h-4 w-24 animate-pulse rounded bg-slate-200" />
          </div>
          <div class="space-y-5 p-4 md:p-5">
            <div v-for="i in 3" :key="i" class="flex animate-pulse gap-3">
              <div class="h-8 w-8 shrink-0 rounded-full bg-slate-200" />
              <div class="flex-1 space-y-2">
                <div class="h-3 w-32 rounded bg-slate-200" />
                <div class="h-3 w-full rounded bg-slate-100" />
                <div class="h-3 w-2/3 rounded bg-slate-100" />
              </div>
            </div>
          </div>
        </div>
      </div>
      <div>
        <!-- History skeleton -->
        <div class="animate-pulse space-y-3 rounded-xl border border-slate-200 bg-white p-5">
          <div class="h-4 w-24 rounded bg-slate-200" />
          <div v-for="i in 4" :key="i" class="space-y-1.5 border-l-2 border-slate-100 pl-3">
            <div class="h-3 w-3/4 rounded bg-slate-100" />
            <div class="h-3 w-1/3 rounded bg-slate-100" />
          </div>
        </div>
      </div>
    </div>
    <div v-else-if="error" class="rounded-md bg-red-50 px-4 py-3 text-sm text-red-700">{{ error }}</div>
    <template v-else-if="item">
      <div class="grid grid-cols-1 gap-6 lg:grid-cols-3">
        <div class="space-y-6 lg:col-span-2">
          <QAInfoPanel :item="item" :members="members" @updated="onUpdated" @removed="onRemoved" />
          <QACommentSection
            :qa-item-id="item.id"
            :comments="comments"
            :members="members"
            @refreshed="comments = $event"
          />
        </div>
        <div>
          <QAHistoryList :entries="history" />
        </div>
      </div>
    </template>
  </section>
</template>
