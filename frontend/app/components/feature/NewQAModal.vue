<script setup lang="ts">
import AppDialog from '~/components/base/AppDialog.vue'
import type { Member, Project, ProjectUpdate, QaCreateRequest, QaItem } from '~/types/api'

const props = defineProps<{
  open: boolean
  projects: Project[]
  updates: ProjectUpdate[]
  members: Member[]
  defaultUpdateId?: number
}>()
const emit = defineEmits<{ close: []; created: [item: QaItem] }>()

const qa = useQa()
const upload = useUpload()

const form = reactive<{
  projectId: number | null
  updateId: number | null
  title: string
  description: string
  category: string
  status: QaCreateRequest['status']
  priority: QaCreateRequest['priority']
  assigneeId: number | null
  images: string[]
}>({
  projectId: null,
  updateId: null,
  title: '',
  description: '',
  category: '',
  status: 'PENDING',
  priority: 'MEDIUM',
  assigneeId: null,
  images: [],
})

const submitting = ref(false)
const uploading = ref(false)
const error = ref<string | null>(null)

const filteredUpdates = computed(() => {
  if (form.projectId == null) return [] as ProjectUpdate[]
  return props.updates.filter((u) => u.projectId === form.projectId)
})

watch(() => props.open, (v) => {
  if (!v) return
  form.projectId = null
  form.updateId = null
  form.title = ''
  form.description = ''
  form.category = ''
  form.status = 'PENDING'
  form.priority = 'MEDIUM'
  form.assigneeId = null
  form.images = []
  error.value = null

  if (props.defaultUpdateId != null) {
    const u = props.updates.find((x) => x.id === props.defaultUpdateId)
    if (u) {
      form.projectId = u.projectId
      form.updateId = u.id
    }
  }
})

watch(() => form.projectId, (pid) => {
  // 프로젝트 바뀌면 updateId 도 첫 번째로 자동 선택
  if (pid == null) {
    form.updateId = null
  } else if (!filteredUpdates.value.find((u) => u.id === form.updateId)) {
    form.updateId = filteredUpdates.value[0]?.id ?? null
  }
})

async function onPickFile(e: Event) {
  const input = e.target as HTMLInputElement
  if (!input.files || input.files.length === 0) return
  uploading.value = true
  try {
    for (const file of Array.from(input.files)) {
      const url = await upload.uploadImage(file, 'qa_image')
      form.images.push(url)
    }
  } catch (e: any) {
    error.value = e?.message ?? '업로드 실패'
  } finally {
    uploading.value = false
    input.value = ''
  }
}

function removeImage(idx: number) {
  form.images.splice(idx, 1)
}

async function onSubmit() {
  error.value = null
  if (form.updateId == null) {
    error.value = '업데이트를 선택하세요.'
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
      assigneeId: form.assigneeId ?? undefined,
      images: form.images,
    })
    emit('created', created)
    emit('close')
  } catch (e: any) {
    error.value = e?.data?.message ?? 'QA 생성에 실패했습니다.'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <AppDialog :open="open" title="새 QA 항목" max-width="max-w-2xl" @close="emit('close')">
    <form id="new-qa-form" class="space-y-4" @submit.prevent="onSubmit">
      <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <label class="block">
          <span class="block text-xs font-medium text-slate-600">프로젝트</span>
          <select
            v-model="form.projectId"
            required
            class="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500"
          >
            <option :value="null" disabled>프로젝트 선택</option>
            <option v-for="p in projects" :key="p.id" :value="p.id">{{ p.name }}</option>
          </select>
        </label>
        <label class="block">
          <span class="block text-xs font-medium text-slate-600">업데이트</span>
          <select
            v-model="form.updateId"
            required
            :disabled="filteredUpdates.length === 0"
            class="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500 disabled:bg-slate-100"
          >
            <option :value="null" disabled>업데이트 선택</option>
            <option v-for="u in filteredUpdates" :key="u.id" :value="u.id">{{ u.version }} – {{ u.title }}</option>
          </select>
        </label>
      </div>

      <label class="block">
        <span class="block text-xs font-medium text-slate-600">제목</span>
        <input
          v-model="form.title"
          type="text"
          required
          maxlength="200"
          class="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500"
        />
      </label>

      <label class="block">
        <span class="block text-xs font-medium text-slate-600">설명</span>
        <textarea
          v-model="form.description"
          rows="3"
          maxlength="4000"
          class="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500"
        />
      </label>

      <div class="grid grid-cols-2 gap-3 sm:grid-cols-4">
        <label class="block sm:col-span-2">
          <span class="block text-xs font-medium text-slate-600">카테고리</span>
          <input
            v-model="form.category"
            type="text"
            maxlength="50"
            class="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500"
          />
        </label>
        <label class="block">
          <span class="block text-xs font-medium text-slate-600">상태</span>
          <select v-model="form.status" class="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500">
            <option value="PENDING">대기중</option>
            <option value="IN_PROGRESS">진행중</option>
            <option value="RESOLVED">해결됨</option>
            <option value="CLOSED">종료</option>
          </select>
        </label>
        <label class="block">
          <span class="block text-xs font-medium text-slate-600">우선순위</span>
          <select v-model="form.priority" class="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500">
            <option value="LOW">낮음</option>
            <option value="MEDIUM">보통</option>
            <option value="HIGH">높음</option>
            <option value="CRITICAL">긴급</option>
          </select>
        </label>
      </div>

      <label class="block">
        <span class="block text-xs font-medium text-slate-600">담당자</span>
        <select
          v-model="form.assigneeId"
          class="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500"
        >
          <option :value="null">미지정</option>
          <option v-for="m in members" :key="m.id" :value="m.id">
            {{ m.name }} · {{ m.role ?? '' }}
          </option>
        </select>
      </label>

      <div>
        <span class="block text-xs font-medium text-slate-600">이미지</span>
        <div class="mt-2 flex flex-wrap gap-2">
          <div
            v-for="(img, i) in form.images"
            :key="img"
            class="relative h-16 w-16 overflow-hidden rounded border border-slate-200"
          >
            <img :src="img" :alt="`image-${i}`" class="h-full w-full object-cover" />
            <button
              type="button"
              class="absolute right-0 top-0 m-0.5 rounded bg-black/60 px-1 text-[10px] text-white"
              @click="removeImage(i)"
            >×</button>
          </div>
          <label class="flex h-16 w-16 cursor-pointer items-center justify-center rounded border border-dashed border-slate-300 text-xs text-slate-400 hover:border-emerald-300 hover:text-emerald-500">
            <input type="file" accept="image/*" multiple class="hidden" @change="onPickFile" />
            {{ uploading ? '업로드…' : '+ 추가' }}
          </label>
        </div>
      </div>

      <p v-if="error" class="rounded bg-red-50 px-3 py-2 text-xs text-red-700">{{ error }}</p>
    </form>

    <template #footer>
      <button type="button" class="rounded-md border border-slate-200 px-3 py-1.5 text-sm text-slate-600 hover:bg-slate-50" @click="emit('close')">취소</button>
      <button
        type="submit"
        form="new-qa-form"
        :disabled="submitting || uploading"
        class="rounded-md bg-emerald-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-emerald-700 disabled:opacity-60"
      >
        {{ submitting ? '생성 중…' : '생성' }}
      </button>
    </template>
  </AppDialog>
</template>
