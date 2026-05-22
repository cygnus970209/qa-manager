<script setup lang="ts">
import AppDialog from '~/components/base/AppDialog.vue'
import type { ProjectUpdate, UpdateCreateRequest } from '~/types/api'

const props = defineProps<{
  open: boolean
  projectId: number
}>()
const emit = defineEmits<{ close: []; created: [update: ProjectUpdate] }>()

const updates = useUpdates()

const form = reactive<{ version: string; title: string; description: string; status: UpdateCreateRequest['status'] }>({
  version: '',
  title: '',
  description: '',
  status: 'IN_PROGRESS',
})
const submitting = ref(false)
const error = ref<string | null>(null)

watch(() => props.open, (v) => {
  if (v) {
    form.version = ''
    form.title = ''
    form.description = ''
    form.status = 'IN_PROGRESS'
    error.value = null
  }
})

async function onSubmit() {
  error.value = null
  submitting.value = true
  try {
    const created = await updates.create(props.projectId, {
      version: form.version,
      title: form.title,
      description: form.description || undefined,
      status: form.status,
    })
    emit('created', created)
    emit('close')
  } catch (e: any) {
    error.value = e?.data?.message ?? '업데이트 생성에 실패했습니다.'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <AppDialog :open="open" title="새 업데이트" @close="emit('close')">
    <form id="new-update-form" class="space-y-4" @submit.prevent="onSubmit">
      <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <label class="block">
          <span class="block text-xs font-medium text-slate-600">버전</span>
          <input
            v-model="form.version"
            type="text"
            required
            maxlength="50"
            placeholder="v1.2.0"
            class="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500"
          />
        </label>
        <label class="block">
          <span class="block text-xs font-medium text-slate-600">상태</span>
          <select
            v-model="form.status"
            class="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500"
          >
            <option value="IN_PROGRESS">진행중</option>
            <option value="TESTING">테스트</option>
            <option value="RELEASED">배포완료</option>
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
      <p v-if="error" class="rounded bg-red-50 px-3 py-2 text-xs text-red-700">{{ error }}</p>
    </form>
    <template #footer>
      <button type="button" class="rounded-md border border-slate-200 px-3 py-1.5 text-sm text-slate-600 hover:bg-slate-50" @click="emit('close')">취소</button>
      <button type="submit" form="new-update-form" :disabled="submitting" class="rounded-md bg-emerald-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-emerald-700 disabled:opacity-60">
        {{ submitting ? '생성 중…' : '생성' }}
      </button>
    </template>
  </AppDialog>
</template>
