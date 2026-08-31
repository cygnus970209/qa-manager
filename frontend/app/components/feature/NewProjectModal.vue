<script setup lang="ts">
import AppDialog from '~/components/base/AppDialog.vue'
import type { GithubRepo, Project, ProjectCreateRequest, ProjectGithubRepoLink, ProjectUpdateRequest } from '~/types/api'

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
const github = useGithub()

const mode = computed<'create' | 'edit'>(() => props.mode ?? 'create')

const form = reactive<{ name: string; description: string; status: ProjectCreateRequest['status'] }>({
  name: '',
  description: '',
  status: 'ACTIVE',
})
const submitting = ref(false)
const error = ref<string | null>(null)

/* ─── GitHub 저장소 연결 (App 이 설정된 경우에만 노출) ─── */
const githubConfigured = ref(false)
const githubRepos = ref<GithubRepo[]>([])
const githubLoading = ref(false)
/** 선택된 repo fullName 목록. 빈 배열은 연결 안 함. */
const selectedRepos = ref<string[]>([])
/** 모달 오픈 시점의 연결 목록 — 변경 여부 판단 + 목록에 없는 기존 연결 병합용. */
const initialRepos = ref<ProjectGithubRepoLink[]>([])

type RepoOption = { fullName: string; installationId: number; owner: string; name: string }

/** 현재 연결된 repo 가 설치 목록에 없어도(권한 회수 등) 선택지로 보이게 병합. */
const repoOptions = computed<RepoOption[]>(() => {
  const opts: RepoOption[] = githubRepos.value.map((r) => ({
    fullName: r.fullName, installationId: r.installationId, owner: r.owner, name: r.name,
  }))
  for (const link of initialRepos.value) {
    const fullName = `${link.repoOwner}/${link.repoName}`
    if (!opts.some((o) => o.fullName === fullName)) {
      opts.unshift({ fullName, installationId: link.installationId, owner: link.repoOwner, name: link.repoName })
    }
  }
  return opts
})

async function loadGithub() {
  githubLoading.value = true
  try {
    const status = await github.appStatus()
    githubConfigured.value = status.configured
    if (status.configured) githubRepos.value = await github.listRepos()
  } catch {
    // 조회 실패 시 섹션 자체를 숨긴다.
    githubConfigured.value = false
  } finally {
    githubLoading.value = false
  }
}

watch(() => props.open, (v) => {
  if (!v) return
  error.value = null
  if (mode.value === 'edit' && props.project) {
    form.name = props.project.name
    form.description = props.project.description ?? ''
    form.status = (props.project.status.toUpperCase()) as ProjectCreateRequest['status']
    initialRepos.value = props.project.githubRepos ?? []
  } else {
    form.name = ''
    form.description = ''
    form.status = 'ACTIVE'
    initialRepos.value = []
  }
  selectedRepos.value = initialRepos.value.map((l) => `${l.repoOwner}/${l.repoName}`)
  loadGithub()
})

/** 선택된 fullName 목록으로 PATCH 필드를 구성한다. 변경 없으면 null. */
function githubPatchFields(): Pick<ProjectUpdateRequest, 'githubRepos'> | null {
  const initial = initialRepos.value.map((l) => `${l.repoOwner}/${l.repoName}`)
  const same = initial.length === selectedRepos.value.length
    && initial.every((f) => selectedRepos.value.includes(f))
  if (same) return null
  const repos = selectedRepos.value
    .map((f) => repoOptions.value.find((o) => o.fullName === f))
    .filter((o): o is RepoOption => !!o)
    .map((o) => ({ installationId: o.installationId, repoOwner: o.owner, repoName: o.name }))
  return { githubRepos: repos }
}

async function onSubmit() {
  error.value = null
  submitting.value = true
  try {
    if (mode.value === 'edit' && props.project) {
      const body: ProjectUpdateRequest = {
        name: form.name,
        description: form.description || undefined,
        status: form.status,
      }
      const gh = githubPatchFields()
      if (gh) Object.assign(body, gh)
      const updated = await projects.update(props.project.id, body)
      emit('updated', updated)
    } else {
      let created = await projects.create({
        name: form.name,
        description: form.description || undefined,
        status: form.status,
      })
      // CreateRequest 에는 repo 필드가 없으므로 생성 성공 후 PATCH 로 연결한다.
      const gh = githubPatchFields()
      if (gh && (gh.githubRepos?.length ?? 0) > 0) {
        try {
          created = await projects.update(created.id, gh)
        } catch (e) {
          // 프로젝트는 이미 생성됨 — repo 연결 실패는 치명적이지 않으므로 로그만 남긴다.
          console.error('NewProjectModal github repo link failed', e)
        }
      }
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
      <div v-if="githubConfigured" class="block">
        <span class="block text-xs font-medium text-slate-600">GitHub 저장소 연결 <span class="font-normal text-slate-400">(여러 개 선택 가능)</span></span>
        <div v-if="githubLoading" class="mt-1 rounded-md border border-slate-200 px-3 py-2 text-xs text-slate-400">저장소 목록 불러오는 중…</div>
        <div v-else-if="repoOptions.length === 0" class="mt-1 rounded-md border border-dashed border-slate-200 px-3 py-2 text-xs text-slate-400">설치된 저장소가 없습니다.</div>
        <div v-else class="mt-1 max-h-40 space-y-0.5 overflow-y-auto rounded-md border border-slate-300 p-1.5">
          <label
            v-for="r in repoOptions"
            :key="r.fullName"
            class="flex cursor-pointer items-center gap-2 rounded px-2 py-1.5 hover:bg-slate-50"
          >
            <input
              v-model="selectedRepos"
              type="checkbox"
              :value="r.fullName"
              class="h-4 w-4 rounded border-slate-300 text-emerald-600 accent-emerald-600 focus:ring-emerald-500"
            />
            <span class="truncate text-sm text-slate-700">{{ r.fullName }}</span>
          </label>
        </div>
        <span class="mt-1 block text-[11px] text-slate-400">연결하면 QA 생성 시 선택한 저장소에 GitHub 이슈를 함께 만들 수 있습니다.</span>
      </div>
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
