<script setup lang="ts">
import AppDialog from '~/components/base/AppDialog.vue'
import type { Project, ProjectCreateRequest } from '~/types/api'

const props = defineProps<{
  open: boolean
  mode?: 'create' | 'edit'
  project?: Project | null
}>()
const emit = defineEmits<{
  close: []
  created: [project: Project]
  updated: [project: Project]
}>()

const projects = useProjects()

const mode = computed<'create' | 'edit'>(() => props.mode ?? 'create')

const form = reactive<{ name: string; description: string; status: ProjectCreateRequest['status'] }>({
  name: '',
  description: '',
  status: 'ACTIVE',
})
const submitting = ref(false)
const error = ref<string | null>(null)

watch(() => props.open, (v) => {
  if (!v) return
  error.value = null
  if (mode.value === 'edit' && props.project) {
    form.name = props.project.name
    form.description = props.project.description ?? ''
    form.status = (props.project.status.toUpperCase()) as ProjectCreateRequest['status']
  } else {
    form.name = ''
    form.description = ''
    form.status = 'ACTIVE'
  }
})

async function onSubmit() {
  error.value = null
  submitting.value = true
  try {
    if (mode.value === 'edit' && props.project) {
      const updated = await projects.update(props.project.id, {
        name: form.name,
        description: form.description || undefined,
        status: form.status,
      })
      emit('updated', updated)
    } else {
      const created = await projects.create({
        name: form.name,
        description: form.description || undefined,
        status: form.status,
      })
      emit('created', created)
    }
    emit('close')
  } catch (e: any) {
    error.value = e?.data?.message ?? (mode.value === 'edit' ? '프로젝트 수정에 실패했습니다.' : '프로젝트 생성에 실패했습니다.')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <AppDialog :open="open" :title="mode === 'edit' ? '프로젝트 수정' : '새 프로젝트'" @close="emit('close')">
    <form id="new-project-form" class="space-y-4" @submit.prevent="onSubmit">
      <label class="block">
        <span class="block text-xs font-medium text-slate-600">프로젝트명</span>
        <input
          v-model="form.name"
          type="text"
          required
          maxlength="100"
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
      <label class="block">
        <span class="block text-xs font-medium text-slate-600">상태</span>
        <select
          v-model="form.status"
          class="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500"
        >
          <option value="ACTIVE">진행중</option>
          <option value="PAUSED">일시중지</option>
          <option value="COMPLETED">완료</option>
        </select>
      </label>
      <p v-if="error" class="rounded bg-red-50 px-3 py-2 text-xs text-red-700">{{ error }}</p>
    </form>
    <template #footer>
      <button
        type="button"
        class="rounded-md border border-slate-200 px-3 py-1.5 text-sm text-slate-600 hover:bg-slate-50"
        @click="emit('close')"
      >취소</button>
      <button
        type="submit"
        form="new-project-form"
        :disabled="submitting"
        class="rounded-md bg-emerald-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-emerald-700 disabled:opacity-60"
      >
        {{ submitting ? (mode === 'edit' ? '저장 중…' : '생성 중…') : (mode === 'edit' ? '저장' : '생성') }}
      </button>
    </template>
  </AppDialog>
</template>
