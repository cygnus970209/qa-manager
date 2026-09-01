<script setup lang="ts">
import { FileText } from '@lucide/vue'
import AppDialog from '~/components/base/AppDialog.vue'
import SearchableSelect from '~/components/base/SearchableSelect.vue'
import QaTagTextarea from '~/components/base/QaTagTextarea.vue'
import { attachmentFileName, isPdfUrl } from '~/utils/attachments'
import type { Member, Project, ProjectUpdate, QaCreateRequest, QaItem } from '~/types/api'

const props = defineProps<{
  open: boolean
  projects: Project[]
  updates: ProjectUpdate[]
  members: Member[]
  defaultUpdateId?: number
  defaultProjectId?: number
}>()
const emit = defineEmits<{ close: []; created: [item: QaItem] }>()

const qa = useQa()
const upload = useUpload()

const auth = useAuthStore()
const { t } = useI18n()

const form = reactive<{
  projectId: number | null
  updateId: number | null
  title: string
  description: string
  category: string
  status: QaCreateRequest['status']
  priority: QaCreateRequest['priority']
  testerId: number | null
  assignee1Id: number | null
  assignee2Id: number | null
  images: string[]
  createGithubIssue: boolean
  /** 이슈를 생성할 repo fullName (프로젝트에 여러 repo 연결 시 선택). */
  githubRepo: string
}>({
  projectId: null,
  updateId: null,
  title: '',
  description: '',
  category: '',
  status: 'NEEDS_FIX',
  priority: 'MEDIUM',
  testerId: null,
  assignee1Id: null,
  assignee2Id: null,
  images: [],
  createGithubIssue: true,
  githubRepo: '',
})

const submitting = ref(false)
const uploading = ref(false)
const error = ref<string | null>(null)

const filteredUpdates = computed(() => {
  if (form.projectId == null) return [] as ProjectUpdate[]
  return props.updates.filter((u) => u.projectId === form.projectId)
})

/** 선택된 프로젝트에 연결된 GitHub repo 목록 (없으면 빈 배열 → 체크박스 숨김). */
const githubRepos = computed(() => {
  const p = props.projects.find((x) => x.id === form.projectId)
  return (p?.githubRepos ?? []).map((r) => ({ ...r, fullName: `${r.repoOwner}/${r.repoName}` }))
})
/** 실제 이슈가 생성될 repo. 선택값이 목록에 없으면 첫 번째 연결 repo 로 폴백. */
const selectedGithubRepo = computed(() =>
  githubRepos.value.find((r) => r.fullName === form.githubRepo) ?? githubRepos.value[0] ?? null,
)

watch(() => props.open, (v) => {
  if (!v) return
  form.projectId = null
  form.updateId = null
  form.title = ''
  form.description = ''
  form.category = ''
  form.status = 'NEEDS_FIX'
  form.priority = 'MEDIUM'
  // tester 기본값: 현재 로그인 사용자
  form.testerId = auth.user?.id ?? null
  form.assignee1Id = null
  form.assignee2Id = null
  form.images = []
  form.createGithubIssue = true
  form.githubRepo = ''
  error.value = null

  // 우선순위: defaultUpdateId > defaultProjectId
  if (props.defaultUpdateId != null) {
    const u = props.updates.find((x) => x.id === props.defaultUpdateId)
    if (u) {
      form.projectId = u.projectId
      form.updateId = u.id
      return
    }
  }
  if (props.defaultProjectId != null) {
    form.projectId = props.defaultProjectId
    // projectId watch 에서 filteredUpdates[0] 자동 선택됨 (최신 업데이트)
  }
})

watch(() => form.projectId, (pid) => {
  // 프로젝트 바뀌면 updateId 도 첫 번째로 자동 선택
  if (pid == null) {
    form.updateId = null
  } else if (!filteredUpdates.value.find((u) => u.id === form.updateId)) {
    form.updateId = filteredUpdates.value[0]?.id ?? null
  }
  // 이슈 대상 repo 도 첫 번째 연결 repo 로 초기화
  form.githubRepo = githubRepos.value[0]?.fullName ?? ''
})

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
    console.error('NewQAModal upload failed', e)
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

/** 클립보드 이미지 paste → 자동 업로드 */
async function onPaste(e: ClipboardEvent) {
  console.debug('[paste] NewQAModal', e.clipboardData?.types, e.clipboardData?.files?.length, e.clipboardData?.items?.length)
  const cd = e.clipboardData
  if (!cd) return
  const files: File[] = []
  if (cd.files && cd.files.length > 0) {
    for (const f of Array.from(cd.files)) {
      if (f.type.startsWith('image/')) files.push(f)
    }
  }
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

/** 사용자가 직접 입력한 내용이 있는지 (백드롭 오클릭으로 내용 날아가는 것 방지용) */
const isDirty = computed(() =>
  form.title.trim() !== ''
  || form.description.trim() !== ''
  || form.category.trim() !== ''
  || form.images.length > 0
  || form.assignee1Id != null
  || form.assignee2Id != null,
)

function requestClose() {
  if (isDirty.value && !window.confirm(t('qa.modal.closeConfirm'))) return
  emit('close')
}

async function onSubmit() {
  error.value = null
  if (form.updateId == null) {
    error.value = t('qa.modal.selectUpdateError')
    return
  }
  submitting.value = true
  try {
    const created = await qa.create({
      updateId: form.updateId,
      title: form.title,
      description: form.description || undefined,
      category: form.category || undefined,
      status: form.status,
      priority: form.priority,
      testerId: form.testerId ?? undefined,
      assignee1Id: form.assignee1Id ?? undefined,
      assignee2Id: form.assignee2Id ?? undefined,
      images: form.images,
      // repo 미연결 프로젝트면 필드 자체를 보내지 않는다.
      createGithubIssue: selectedGithubRepo.value && form.createGithubIssue ? true : undefined,
      githubRepoOwner: selectedGithubRepo.value && form.createGithubIssue ? selectedGithubRepo.value.repoOwner : undefined,
      githubRepoName: selectedGithubRepo.value && form.createGithubIssue ? selectedGithubRepo.value.repoName : undefined,
    })
    emit('created', created)
    emit('close')
  } catch (e: any) {
    error.value = e?.data?.message ?? t('qa.modal.createFailed')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <AppDialog :open="open" :title="$t('qa.modal.title')" max-width="max-w-2xl" @close="requestClose">
    <form id="new-qa-form" class="space-y-4" @submit.prevent="onSubmit">
      <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <label class="block">
          <span class="block text-xs font-medium text-slate-600">{{ $t('qa.fields.project') }}</span>
          <select
            v-model="form.projectId"
            required
            class="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500"
          >
            <option :value="null" disabled>{{ $t('qa.modal.selectProject') }}</option>
            <option v-for="p in projects" :key="p.id" :value="p.id">{{ p.name }}</option>
          </select>
        </label>
        <label class="block">
          <span class="block text-xs font-medium text-slate-600">{{ $t('qa.fields.update') }}</span>
          <select
            v-model="form.updateId"
            required
            :disabled="filteredUpdates.length === 0"
            class="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500 disabled:bg-slate-100"
          >
            <option :value="null" disabled>{{ $t('qa.modal.selectUpdate') }}</option>
            <option v-for="u in filteredUpdates" :key="u.id" :value="u.id">{{ u.version }} – {{ u.title }}</option>
          </select>
        </label>
      </div>

      <div v-if="githubRepos.length > 0" class="flex flex-wrap items-center gap-2 rounded-md bg-slate-50 px-3 py-2">
        <label class="flex cursor-pointer items-center gap-2">
          <input
            v-model="form.createGithubIssue"
            type="checkbox"
            class="h-4 w-4 rounded border-slate-300 text-emerald-600 accent-emerald-600 focus:ring-emerald-500"
          />
          <span class="text-xs text-slate-600">{{ $t('qa.modal.createGithubIssue') }}</span>
        </label>
        <span v-if="githubRepos.length === 1" class="text-xs text-slate-400">({{ githubRepos[0]?.fullName }})</span>
        <select
          v-else
          v-model="form.githubRepo"
          :disabled="!form.createGithubIssue"
          class="min-w-0 flex-1 rounded-md border border-slate-200 bg-white px-2 py-1 text-xs focus:outline-none focus:ring-2 focus:ring-emerald-200 disabled:opacity-50"
        >
          <option v-for="r in githubRepos" :key="r.fullName" :value="r.fullName">{{ r.fullName }}</option>
        </select>
      </div>

      <label class="block">
        <span class="block text-xs font-medium text-slate-600">{{ $t('qa.fields.title') }}</span>
        <input
          v-model="form.title"
          type="text"
          required
          maxlength="200"
          class="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500"
        />
      </label>

      <label class="block">
        <span class="block text-xs font-medium text-slate-600">{{ $t('qa.fields.description') }}</span>
        <QaTagTextarea
          v-model="form.description"
          rows="3"
          maxlength="4000"
          :placeholder="$t('qa.modal.descriptionPlaceholder')"
          class="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500"
          @paste="onPaste"
        />
      </label>

      <div class="grid grid-cols-2 gap-3 sm:grid-cols-4">
        <label class="block sm:col-span-2">
          <span class="block text-xs font-medium text-slate-600">{{ $t('qa.fields.category') }}</span>
          <input
            v-model="form.category"
            type="text"
            maxlength="50"
            class="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500"
          />
        </label>
        <label class="block">
          <span class="block text-xs font-medium text-slate-600">{{ $t('qa.fields.status') }}</span>
          <select v-model="form.status" class="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500">
            <option value="NEEDS_FIX">{{ $t('common.qaStatus.needs_fix') }}</option>
            <option value="IN_PROGRESS">{{ $t('common.qaStatus.in_progress') }}</option>
            <option value="FIX_DONE">{{ $t('common.qaStatus.fix_done') }}</option>
            <option value="CONFIRMED">{{ $t('common.qaStatus.confirmed') }}</option>
            <option value="ON_HOLD">{{ $t('common.qaStatus.on_hold') }}</option>
            <option value="NEEDS_RECHECK">{{ $t('common.qaStatus.needs_recheck') }}</option>
          </select>
        </label>
        <label class="block">
          <span class="block text-xs font-medium text-slate-600">{{ $t('qa.fields.priority') }}</span>
          <select v-model="form.priority" class="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500">
            <option value="LOW">{{ $t('common.priority.low') }}</option>
            <option value="MEDIUM">{{ $t('common.priority.medium') }}</option>
            <option value="HIGH">{{ $t('common.priority.high') }}</option>
            <option value="CRITICAL">{{ $t('common.priority.critical') }}</option>
          </select>
        </label>
      </div>

      <div class="grid grid-cols-1 gap-3 sm:grid-cols-3">
        <div>
          <span class="block text-xs font-medium text-slate-600">{{ $t('qa.fields.testerAuthor') }}</span>
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
          <span class="block text-xs font-medium text-slate-600">{{ $t('common.roles.assignee1') }}</span>
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
          <span class="block text-xs font-medium text-slate-600">{{ $t('common.roles.assignee2') }}</span>
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

      <div>
        <span class="block text-xs font-medium text-slate-600">{{ $t('qa.fields.attachments') }}</span>
        <div class="mt-2 flex flex-wrap gap-2">
          <div
            v-for="(img, i) in form.images"
            :key="img"
            class="relative h-16 w-16 overflow-hidden rounded border border-slate-200"
          >
            <div
              v-if="isPdfUrl(img)"
              :title="attachmentFileName(img)"
              class="flex h-full w-full flex-col items-center justify-center gap-0.5 bg-slate-50 px-1"
            >
              <FileText class="h-5 w-5 shrink-0 text-rose-500" />
              <span class="w-full truncate text-center text-[9px] leading-tight text-slate-500">{{ attachmentFileName(img) }}</span>
            </div>
            <img v-else :src="img" :alt="`image-${i}`" class="h-full w-full object-cover" />
            <button
              type="button"
              class="absolute right-0 top-0 m-0.5 rounded bg-black/60 px-1 text-[10px] text-white"
              @click="removeImage(i)"
            >×</button>
          </div>
          <label class="flex h-16 w-16 cursor-pointer items-center justify-center rounded border border-dashed border-slate-300 text-xs text-slate-400 hover:border-emerald-300 hover:text-emerald-500">
            <input type="file" accept="image/*,application/pdf" multiple class="hidden" @change="onPickFile" />
            {{ uploading ? $t('qa.upload.uploading') : $t('qa.upload.add') }}
          </label>
        </div>
      </div>

      <p v-if="error" class="rounded bg-red-50 px-3 py-2 text-xs text-red-700">{{ error }}</p>
    </form>

    <template #footer>
      <button type="button" class="rounded-md border border-slate-200 px-3 py-1.5 text-sm text-slate-600 hover:bg-slate-50" @click="requestClose">{{ $t('common.actions.cancel') }}</button>
      <button
        type="submit"
        form="new-qa-form"
        :disabled="submitting || uploading"
        class="rounded-md bg-emerald-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-emerald-700 disabled:opacity-60"
      >
        {{ submitting ? $t('qa.modal.creating') : $t('qa.modal.create') }}
      </button>
    </template>
  </AppDialog>
</template>
