<script setup lang="ts">
import { CircleDot, FileText, GitCommitHorizontal, Loader2, Plus, Save, Trash2 } from '@lucide/vue'
import StatusBadge from '~/components/base/StatusBadge.vue'
import PriorityBadge from '~/components/base/PriorityBadge.vue'
import ImageLightbox from '~/components/base/ImageLightbox.vue'
import QaRefText from '~/components/base/QaRefText.vue'
import QaTagTextarea from '~/components/base/QaTagTextarea.vue'
import SearchableSelect from '~/components/base/SearchableSelect.vue'
import { attachmentFileName, isPdfUrl, openPdfInNewTab } from '~/utils/attachments'
import type { GithubCommit, Member, ProjectUpdate, QaItem, QaPatchRequest, QaStatusUpper } from '~/types/api'

const props = defineProps<{
  item: QaItem
  members: Member[]
  /** 이 QA가 이동 가능한 업데이트(버전) 목록 — 같은 프로젝트 범위. */
  updates?: ProjectUpdate[]
  /** 설명 편집 시 # 태그 자동완성 후보 (미전달 시 지연 로드). */
  qaItems?: QaItem[]
}>()
const emit = defineEmits<{
  updated: [item: QaItem]
  removed: []
  addQa: []
}>()

const qaApi = useQa()
const upload = useUpload()
const github = useGithub()
const { t } = useI18n()
const { confirmDialog } = useAppDialog()

const editing = ref(false)
const saving = ref(false)
const uploading = ref(false)
const error = ref<string | null>(null)
const lightboxIndex = ref<number | null>(null)
const attachments = computed<string[]>(() => (editing.value ? form.images : props.item.images))
const lightboxImages = computed<string[]>(() => attachments.value.filter((u) => !isPdfUrl(u)))
/** 전체 첨부 목록 인덱스 → 이미지만 모은 라이트박스 인덱스로 변환해 연다. */
function openLightboxAt(i: number) {
  lightboxIndex.value = attachments.value.slice(0, i).filter((u) => !isPdfUrl(u)).length
}

const form = reactive<{
  title: string
  description: string
  category: string
  status: QaItem['status']
  priority: QaItem['priority']
  testerId: number | null
  assignee1Id: number | null
  assignee2Id: number | null
  images: string[]
}>({
  title: props.item.title,
  description: props.item.description ?? '',
  category: props.item.category ?? '',
  status: props.item.status,
  priority: props.item.priority,
  testerId: props.item.tester?.id ?? null,
  assignee1Id: props.item.assignee1?.id ?? null,
  assignee2Id: props.item.assignee2?.id ?? null,
  images: [...props.item.images],
})

watch(() => props.item, (next) => {
  form.title = next.title
  form.description = next.description ?? ''
  form.category = next.category ?? ''
  form.status = next.status
  form.priority = next.priority
  form.testerId = next.tester?.id ?? null
  form.assignee1Id = next.assignee1?.id ?? null
  form.assignee2Id = next.assignee2?.id ?? null
  form.images = [...next.images]
})

const upperStatus = computed<QaStatusUpper>(() => form.status.toUpperCase() as QaStatusUpper)
const upperPriority = computed<'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'>(() => {
  switch (form.priority) {
    case 'low':      return 'LOW'
    case 'medium':   return 'MEDIUM'
    case 'high':     return 'HIGH'
    case 'critical': return 'CRITICAL'
  }
})

async function onSave() {
  saving.value = true
  error.value = null
  try {
    const body: QaPatchRequest = {
      title: form.title,
      description: form.description,
      category: form.category || undefined,
      status: upperStatus.value,
      priority: upperPriority.value,
      images: form.images,
    }
    if (form.testerId == null) body.clearTester = true
    else body.testerId = form.testerId
    if (form.assignee1Id == null) body.clearAssignee1 = true
    else body.assignee1Id = form.assignee1Id
    if (form.assignee2Id == null) body.clearAssignee2 = true
    else body.assignee2Id = form.assignee2Id
    const updated = await qaApi.update(props.item.id, body)
    emit('updated', updated)
    editing.value = false
  } catch (e: any) {
    error.value = e?.data?.message ?? t('qa.info.saveFailed')
  } finally {
    saving.value = false
  }
}

function ensureNamed(f: File): File {
  if (f.name && f.name.length > 0) return f
  const ext = (f.type.split('/')[1] ?? 'png').replace(/[^a-z0-9]/gi, '')
  return new File([f], `clipboard-${Date.now()}.${ext}`, { type: f.type })
}

async function uploadFiles(files: Iterable<File>) {
  uploading.value = true
  error.value = null
  try {
    for (const file of files) {
      const url = await upload.uploadFile(ensureNamed(file), 'qa_image')
      form.images.push(url)
    }
  } catch (e: any) {
    console.error('QAInfoPanel upload failed', e)
    error.value = e?.data?.message ?? e?.message ?? t('qa.upload.failed')
  } finally {
    uploading.value = false
  }
}

async function onPickFile(e: Event) {
  const input = e.target as HTMLInputElement
  if (!input.files || input.files.length === 0) return
  await uploadFiles(Array.from(input.files))
  input.value = ''
}

/** 클립보드 이미지 paste → 자동 업로드 (편집 모드에서만 동작) */
async function onPaste(e: ClipboardEvent) {
  console.debug('[paste] QAInfoPanel editing=', editing.value, e.clipboardData?.types, e.clipboardData?.files?.length, e.clipboardData?.items?.length)
  if (!editing.value) return
  const cd = e.clipboardData
  if (!cd) return
  const files: File[] = []
  // 1) 일부 브라우저는 files 에 직접 노출
  if (cd.files && cd.files.length > 0) {
    for (const f of Array.from(cd.files)) {
      if (f.type.startsWith('image/')) files.push(f)
    }
  }
  // 2) items fallback
  if (files.length === 0 && cd.items) {
    for (const item of Array.from(cd.items)) {
      if (item.kind === 'file' && item.type.startsWith('image/')) {
        const f = item.getAsFile()
        if (f) files.push(f)
      }
    }
  }
  if (files.length === 0) return
  e.preventDefault()
  await uploadFiles(files)
}

function removeImage(idx: number) {
  form.images.splice(idx, 1)
}

async function onDelete() {
  if (!(await confirmDialog({ message: t('qa.info.deleteConfirm'), danger: true }))) return
  await qaApi.remove(props.item.id)
  emit('removed')
}

function statusToUpper(s: QaItem['status']): QaStatusUpper {
  return s.toUpperCase() as QaStatusUpper
}

/** 다른 업데이트(버전)로 QA 이동 (즉시 PATCH) */
async function onMoveUpdate(updateId: number) {
  if (updateId === props.item.updateId) return
  saving.value = true
  error.value = null
  try {
    const updated = await qaApi.update(props.item.id, { updateId })
    emit('updated', updated)
  } catch (e: any) {
    error.value = e?.data?.message ?? t('qa.info.moveUpdateFailed')
  } finally {
    saving.value = false
  }
}

/** 편집 모드 진입 없이 상태 단독 변경 (즉시 PATCH) */
async function onQuickStatusChange(next: QaItem['status']) {
  if (next === props.item.status) return
  saving.value = true
  error.value = null
  try {
    const updated = await qaApi.update(props.item.id, { status: statusToUpper(next) })
    emit('updated', updated)
    form.status = next
  } catch (e: any) {
    error.value = e?.data?.message ?? t('qa.info.statusChangeFailed')
  } finally {
    saving.value = false
  }
}

/* ─── GitHub 연결 커밋 (이슈가 연결된 QA 에서만 조회) ─── */
const commits = ref<GithubCommit[]>([])
const commitsLoading = ref(false)

async function loadCommits() {
  if (!props.item.githubIssue) {
    commits.value = []
    return
  }
  commitsLoading.value = true
  try {
    commits.value = await github.qaCommits(props.item.id)
  } catch {
    // 조회 실패 시 빈 목록으로 표시 (커밋은 보조 정보).
    commits.value = []
  } finally {
    commitsLoading.value = false
  }
}
onMounted(loadCommits)
// 이전/다음 이동으로 item 이 교체될 때만 재조회 (같은 항목의 PATCH 반영에는 재요청하지 않음).
watch(() => props.item.id, loadCommits)

/** 커밋 메시지 첫 줄만 표시. */
function commitTitle(message: string): string {
  return message.split('\n')[0] ?? message
}

/** 편집 모드 진입 없이 담당자(tester/assignee1/assignee2) 단독 변경 (즉시 PATCH) */
async function onQuickMemberChange(slot: 'tester' | 'assignee1' | 'assignee2', id: number | null) {
  const currentId =
    slot === 'tester' ? (props.item.tester?.id ?? null)
    : slot === 'assignee1' ? (props.item.assignee1?.id ?? null)
    : (props.item.assignee2?.id ?? null)
  if (currentId === id) return
  saving.value = true
  error.value = null
  try {
    const body: QaPatchRequest = {}
    if (slot === 'tester') {
      if (id == null) body.clearTester = true
      else body.testerId = id
    } else if (slot === 'assignee1') {
      if (id == null) body.clearAssignee1 = true
      else body.assignee1Id = id
    } else {
      if (id == null) body.clearAssignee2 = true
      else body.assignee2Id = id
    }
    const updated = await qaApi.update(props.item.id, body)
    emit('updated', updated)
    if (slot === 'tester') form.testerId = id
    else if (slot === 'assignee1') form.assignee1Id = id
    else form.assignee2Id = id
  } catch (e: any) {
    error.value = e?.data?.message ?? t('qa.info.assigneeChangeFailed')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <article class="rounded-xl border border-slate-200 bg-white p-5 md:p-6 dark:border-slate-800 dark:bg-slate-900">
    <!-- 상단: QA 번호(좌) + 액션 버튼(우). 제목은 아랫줄에서 전체 폭 사용 → 긴 제목에도 안 깨진다. -->
    <div class="mb-2 flex items-center justify-between gap-3">
      <p class="text-xs font-semibold tabular-nums text-slate-400 dark:text-slate-500">QA #{{ item.id }}</p>
      <div class="flex shrink-0 items-center gap-2">
        <button
          v-if="!editing"
          type="button"
          class="inline-flex items-center gap-1 whitespace-nowrap rounded-md bg-blue-500 px-3 py-1.5 text-xs font-medium text-white hover:bg-blue-600"
          @click="emit('addQa')"
        >
          <Plus class="h-3.5 w-3.5" /> {{ $t('qa.info.newQa') }}
        </button>
        <button v-if="!editing" type="button" class="rounded-md border border-slate-200 px-3 py-1.5 text-xs text-slate-600 hover:bg-slate-50 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-800/60" @click="editing = true">{{ $t('common.actions.edit') }}</button>
        <button v-else type="button" class="rounded-md border border-slate-200 px-3 py-1.5 text-xs text-slate-600 hover:bg-slate-50 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-800/60" @click="editing = false">{{ $t('common.actions.cancel') }}</button>
        <button
          v-if="editing"
          type="button"
          :disabled="saving"
          class="inline-flex items-center gap-1 rounded-md bg-emerald-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-emerald-700 disabled:opacity-60"
          @click="onSave"
        >
          <Save class="h-3.5 w-3.5" /> {{ saving ? $t('common.state.saving') : $t('common.actions.save') }}
        </button>
        <button
          v-if="!editing"
          type="button"
          class="inline-flex items-center gap-1 rounded-md border border-rose-200 bg-rose-50 px-3 py-1.5 text-xs font-medium text-rose-600 hover:bg-rose-100 dark:border-rose-500/20 dark:bg-rose-500/10 dark:text-rose-400 dark:hover:bg-rose-500/20"
          @click="onDelete"
        >
          <Trash2 class="h-3.5 w-3.5" /> {{ $t('common.actions.delete') }}
        </button>
      </div>
    </div>
    <div class="min-w-0">
      <input
        v-if="editing"
        v-model="form.title"
        class="w-full rounded-md border border-slate-300 px-3 py-2 text-lg font-bold focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100"
      />
      <h1 v-else class="break-words text-xl font-bold text-slate-800 md:text-2xl dark:text-slate-100">{{ item.title }}</h1>
    </div>

    <!-- 메타 -->
    <div class="mt-3 flex flex-wrap items-center gap-2 text-xs text-slate-500 dark:text-slate-400">
      <span v-if="item.category" class="rounded-md bg-slate-100 px-2 py-0.5 dark:bg-slate-800">{{ item.category }}</span>
      <!-- 업데이트(버전) 이동: 같은 프로젝트의 다른 버전으로 즉시 이동 -->
      <div v-if="(updates?.length ?? 0) > 0" class="inline-flex items-center gap-1">
        <span class="text-slate-400 dark:text-slate-500">{{ $t('qa.fields.update') }}</span>
        <select
          :value="item.updateId"
          :disabled="saving"
          class="max-w-[180px] truncate rounded-md border border-slate-200 bg-white px-2 py-1 text-xs focus:outline-none focus:ring-2 focus:ring-emerald-200 disabled:opacity-50 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100 dark:focus:ring-emerald-500/20"
          @change="onMoveUpdate(Number(($event.target as HTMLSelectElement).value))"
        >
          <option v-for="u in updates" :key="u.id" :value="u.id">{{ u.version }} · {{ u.title }}</option>
        </select>
      </div>
      <!-- 비편집 모드에서도 상태 즉시 변경 가능 -->
      <select
        v-if="!editing"
        :value="item.status"
        :disabled="saving"
        class="rounded-md border border-slate-200 bg-white px-2 py-1 text-xs focus:outline-none focus:ring-2 focus:ring-emerald-200 disabled:opacity-50 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100 dark:focus:ring-emerald-500/20"
        @change="onQuickStatusChange(($event.target as HTMLSelectElement).value as any)"
      >
        <option value="needs_fix">{{ $t('common.qaStatus.needs_fix') }}</option>
        <option value="in_progress">{{ $t('common.qaStatus.in_progress') }}</option>
        <option value="fix_done">{{ $t('common.qaStatus.fix_done') }}</option>
        <option value="confirmed">{{ $t('common.qaStatus.confirmed') }}</option>
        <option value="on_hold">{{ $t('common.qaStatus.on_hold') }}</option>
        <option value="needs_recheck">{{ $t('common.qaStatus.needs_recheck') }}</option>
      </select>
      <PriorityBadge :priority="item.priority" />
      <!-- GitHub 이슈 배지 (open=초록, closed=보라) -->
      <a
        v-if="item.githubIssue"
        :href="item.githubIssue.issueUrl"
        target="_blank"
        rel="noopener noreferrer"
        :title="`${item.githubIssue.repoOwner}/${item.githubIssue.repoName}`"
        :class="[
          'inline-flex items-center gap-1 whitespace-nowrap rounded-full px-2.5 py-0.5 text-xs font-medium hover:underline',
          item.githubIssue.state === 'open' ? 'bg-emerald-50 text-emerald-600 dark:bg-emerald-500/10 dark:text-emerald-400' : 'bg-purple-50 text-purple-600 dark:bg-purple-500/10 dark:text-purple-400',
        ]"
      >
        <CircleDot class="h-3 w-3" />
        #{{ item.githubIssue.issueNumber }} · {{ item.githubIssue.state }}
      </a>
      <!-- 테스터/담당자는 항상 다음 줄로 분리 (flex-wrap 강제 개행) -->
      <div class="basis-full" aria-hidden="true"></div>
      <!-- 비편집 모드에서 담당자도 즉시 변경 가능 (상태와 동일한 패턴) -->
      <template v-if="!editing">
        <div class="inline-flex items-center gap-1">
          <span class="text-slate-400 dark:text-slate-500">{{ $t('common.roles.tester') }}</span>
          <SearchableSelect
            class="w-32"
            :model-value="item.tester?.id ?? null"
            :options="members"
            :key-fn="(m: Member) => m.id"
            :label-fn="(m: Member) => m.name"
            :search-fn="(m: Member) => m.role ?? ''"
            :placeholder="$t('common.actions.search')"
            :empty-label="$t('qa.common.unassigned')"
            clearable
            :disabled="saving"
            @update:model-value="(v) => onQuickMemberChange('tester', v as number | null)"
          />
        </div>
        <div class="inline-flex items-center gap-1">
          <span class="text-slate-400 dark:text-slate-500">{{ $t('common.roles.assignee1') }}</span>
          <SearchableSelect
            class="w-32"
            :model-value="item.assignee1?.id ?? null"
            :options="members"
            :key-fn="(m: Member) => m.id"
            :label-fn="(m: Member) => m.name"
            :search-fn="(m: Member) => m.role ?? ''"
            :placeholder="$t('common.actions.search')"
            :empty-label="$t('qa.common.unassigned')"
            clearable
            :disabled="saving"
            @update:model-value="(v) => onQuickMemberChange('assignee1', v as number | null)"
          />
        </div>
        <div class="inline-flex items-center gap-1">
          <span class="text-slate-400 dark:text-slate-500">{{ $t('common.roles.assignee2') }}</span>
          <SearchableSelect
            class="w-32"
            :model-value="item.assignee2?.id ?? null"
            :options="members"
            :key-fn="(m: Member) => m.id"
            :label-fn="(m: Member) => m.name"
            :search-fn="(m: Member) => m.role ?? ''"
            :placeholder="$t('common.actions.search')"
            :empty-label="$t('qa.common.unassigned')"
            clearable
            :disabled="saving"
            @update:model-value="(v) => onQuickMemberChange('assignee2', v as number | null)"
          />
        </div>
      </template>
      <template v-else>
        <span>{{ $t('common.roles.tester') }} {{ item.tester?.name ?? $t('qa.common.unassigned') }}</span>
        <span>·</span>
        <span>
          {{ $t('common.roles.assignee') }}
          <template v-if="item.assignee1 || item.assignee2">
            {{ [item.assignee1?.name, item.assignee2?.name].filter(Boolean).join(', ') }}
          </template>
          <template v-else>{{ $t('qa.common.unassigned') }}</template>
        </span>
      </template>
    </div>

    <!-- 본문 -->
    <div class="mt-5">
      <h2 class="mb-2 text-xs font-medium text-slate-500 dark:text-slate-400">{{ $t('qa.fields.description') }}</h2>
      <QaTagTextarea
        v-if="editing"
        v-model="form.description"
        :qa-items="qaItems"
        :exclude-id="item.id"
        rows="5"
        :placeholder="$t('qa.info.descriptionPlaceholder')"
        class="w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100 dark:placeholder-slate-500"
        @paste="onPaste"
      />
      <p v-else class="whitespace-pre-wrap text-sm text-slate-700 dark:text-slate-200">
        <QaRefText v-if="item.description" :text="item.description" />
        <template v-else>—</template>
      </p>
    </div>

    <!-- 편집 모드: 상태/우선순위/카테고리 + 테스터/담당자1/담당자2 -->
    <div v-if="editing" class="mt-5 space-y-3">
      <div class="grid grid-cols-2 gap-3 md:grid-cols-3">
        <label class="block">
          <span class="block text-xs text-slate-500 dark:text-slate-400">{{ $t('qa.fields.status') }}</span>
          <select v-model="form.status" class="mt-1 w-full rounded-md border border-slate-300 px-2 py-1.5 text-xs dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100">
            <option value="needs_fix">{{ $t('common.qaStatus.needs_fix') }}</option>
            <option value="in_progress">{{ $t('common.qaStatus.in_progress') }}</option>
            <option value="fix_done">{{ $t('common.qaStatus.fix_done') }}</option>
            <option value="confirmed">{{ $t('common.qaStatus.confirmed') }}</option>
            <option value="on_hold">{{ $t('common.qaStatus.on_hold') }}</option>
            <option value="needs_recheck">{{ $t('common.qaStatus.needs_recheck') }}</option>
          </select>
        </label>
        <label class="block">
          <span class="block text-xs text-slate-500 dark:text-slate-400">{{ $t('qa.fields.priority') }}</span>
          <select v-model="form.priority" class="mt-1 w-full rounded-md border border-slate-300 px-2 py-1.5 text-xs dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100">
            <option value="low">{{ $t('common.priority.low') }}</option>
            <option value="medium">{{ $t('common.priority.medium') }}</option>
            <option value="high">{{ $t('common.priority.high') }}</option>
            <option value="critical">{{ $t('common.priority.critical') }}</option>
          </select>
        </label>
        <label class="block">
          <span class="block text-xs text-slate-500 dark:text-slate-400">{{ $t('qa.fields.category') }}</span>
          <input v-model="form.category" maxlength="50" class="mt-1 w-full rounded-md border border-slate-300 px-2 py-1.5 text-xs dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100" />
        </label>
      </div>
      <div class="grid grid-cols-1 gap-3 md:grid-cols-3">
        <div>
          <span class="block text-xs text-slate-500 dark:text-slate-400">{{ $t('qa.fields.testerAuthor') }}</span>
          <SearchableSelect
            class="mt-1"
            :model-value="form.testerId"
            :options="members"
            :key-fn="(m: Member) => m.id"
            :label-fn="(m: Member) => m.name"
            :search-fn="(m: Member) => m.role ?? ''"
            :placeholder="$t('qa.common.memberSearchPlaceholder')"
            :empty-label="$t('qa.common.unassigned')"
            clearable
            @update:model-value="(v) => form.testerId = v as number | null"
          />
        </div>
        <div>
          <span class="block text-xs text-slate-500 dark:text-slate-400">{{ $t('common.roles.assignee1') }}</span>
          <SearchableSelect
            class="mt-1"
            :model-value="form.assignee1Id"
            :options="members"
            :key-fn="(m: Member) => m.id"
            :label-fn="(m: Member) => m.name"
            :search-fn="(m: Member) => m.role ?? ''"
            :placeholder="$t('qa.common.memberSearchPlaceholder')"
            :empty-label="$t('qa.common.unassigned')"
            clearable
            @update:model-value="(v) => form.assignee1Id = v as number | null"
          />
        </div>
        <div>
          <span class="block text-xs text-slate-500 dark:text-slate-400">{{ $t('common.roles.assignee2') }}</span>
          <SearchableSelect
            class="mt-1"
            :model-value="form.assignee2Id"
            :options="members"
            :key-fn="(m: Member) => m.id"
            :label-fn="(m: Member) => m.name"
            :search-fn="(m: Member) => m.role ?? ''"
            :placeholder="$t('qa.common.memberSearchPlaceholder')"
            :empty-label="$t('qa.common.unassigned')"
            clearable
            @update:model-value="(v) => form.assignee2Id = v as number | null"
          />
        </div>
      </div>
    </div>

    <!-- 첨부 파일 -->
    <div class="mt-5">
      <h2 class="mb-2 text-xs font-medium text-slate-500 dark:text-slate-400">{{ $t('qa.fields.attachments') }}</h2>
      <div class="flex flex-wrap gap-2">
        <div
          v-for="(img, i) in attachments"
          :key="img + i"
          class="relative h-24 w-24 overflow-hidden rounded border border-slate-200 dark:border-slate-800"
        >
          <button
            v-if="isPdfUrl(img)"
            type="button"
            :title="attachmentFileName(img)"
            class="flex h-full w-full flex-col items-center justify-center gap-1 bg-slate-50 px-1.5 transition-colors hover:bg-slate-100 dark:bg-slate-800/50 dark:hover:bg-slate-800"
            @click="openPdfInNewTab(img)"
          >
            <FileText class="h-7 w-7 shrink-0 text-rose-500 dark:text-rose-400" />
            <span class="w-full truncate text-center text-[10px] leading-tight text-slate-500 dark:text-slate-400">{{ attachmentFileName(img) }}</span>
          </button>
          <button
            v-else
            type="button"
            class="block h-full w-full cursor-zoom-in"
            @click="openLightboxAt(i)"
          >
            <img :src="img" :alt="`image-${i}`" class="h-full w-full object-cover transition-colors hover:opacity-90" />
          </button>
          <button
            v-if="editing"
            type="button"
            class="absolute right-0 top-0 m-0.5 rounded bg-black/60 px-1 text-[10px] text-white"
            @click.stop="removeImage(i)"
          >×</button>
        </div>
        <label
          v-if="editing"
          class="flex h-24 w-24 cursor-pointer items-center justify-center rounded border border-dashed border-slate-300 text-xs text-slate-400 hover:border-emerald-300 hover:text-emerald-500 dark:border-slate-700 dark:text-slate-500 dark:hover:text-emerald-400"
        >
          <input type="file" accept="image/*,application/pdf" multiple class="hidden" @change="onPickFile" />
          {{ uploading ? $t('qa.upload.uploading') : $t('qa.upload.add') }}
        </label>
        <p v-if="attachments.length === 0 && !editing" class="text-xs text-slate-400 dark:text-slate-500">{{ $t('qa.info.noAttachments') }}</p>
      </div>
    </div>

    <!-- GitHub 연결 커밋 (이슈가 연결된 QA 에서만) -->
    <div v-if="item.githubIssue" class="mt-5">
      <h2 class="mb-2 flex items-center gap-1.5 text-xs font-medium text-slate-500 dark:text-slate-400">
        <GitCommitHorizontal class="h-3.5 w-3.5" /> {{ $t('qa.info.linkedCommits') }}
      </h2>
      <div v-if="commitsLoading" class="flex items-center gap-2 py-2 text-xs text-slate-400 dark:text-slate-500">
        <Loader2 class="h-3.5 w-3.5 animate-spin" /> {{ $t('qa.info.loadingCommits') }}
      </div>
      <p v-else-if="commits.length === 0" class="rounded-lg border border-dashed border-slate-200 px-3 py-4 text-center text-xs text-slate-400 dark:border-slate-800 dark:text-slate-500">
        {{ $t('qa.info.commitsHint') }}
      </p>
      <ul v-else class="divide-y divide-slate-100 overflow-hidden rounded-lg border border-slate-100 dark:divide-slate-800 dark:border-slate-800">
        <li v-for="c in commits" :key="c.sha" class="flex items-center gap-3 px-3 py-2">
          <a
            :href="c.htmlUrl"
            target="_blank"
            rel="noopener noreferrer"
            class="shrink-0 rounded bg-slate-100 px-1.5 py-0.5 font-mono text-[11px] text-slate-600 hover:bg-emerald-50 hover:text-emerald-600 dark:bg-slate-800 dark:text-slate-300 dark:hover:bg-emerald-500/10 dark:hover:text-emerald-400"
          >{{ c.shortSha }}</a>
          <span class="min-w-0 flex-1 truncate text-sm text-slate-700 dark:text-slate-200" :title="c.message">{{ commitTitle(c.message) }}</span>
          <span class="hidden shrink-0 items-center gap-1.5 text-xs text-slate-400 sm:flex dark:text-slate-500">
            <img v-if="c.avatarUrl" :src="c.avatarUrl" :alt="c.authorName ?? c.authorLogin ?? ''" class="h-4 w-4 rounded-full bg-slate-100 dark:bg-slate-800" />
            {{ c.authorName ?? c.authorLogin ?? '-' }}
          </span>
          <span class="shrink-0 text-xs tabular-nums text-slate-400 dark:text-slate-500">{{ c.committedAt ? c.committedAt.slice(0, 10) : '-' }}</span>
        </li>
      </ul>
    </div>

    <!-- 생성/수정 날짜 (보조 정보, 본문 하단) -->
    <div class="mt-4 text-[11px] text-slate-400 dark:text-slate-500">
      {{ $t('qa.info.dates', { created: item.createdAt?.slice(0, 10) ?? '', updated: item.updatedAt?.slice(0, 10) ?? '' }) }}
    </div>

    <p v-if="error" class="mt-3 rounded bg-red-50 px-3 py-2 text-xs text-red-700 dark:bg-red-500/10 dark:text-red-400">{{ error }}</p>

    <ImageLightbox
      :images="lightboxImages"
      :index="lightboxIndex"
      @close="lightboxIndex = null"
      @update:index="lightboxIndex = $event"
    />
  </article>
</template>
