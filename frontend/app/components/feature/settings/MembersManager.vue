<script setup lang="ts">
import { Edit3, KeyRound, Send, ShieldCheck, Trash2, UserPlus } from '@lucide/vue'
import DeleteConfirmModal from '~/components/base/DeleteConfirmModal.vue'
import MemberModal from '~/components/feature/MemberModal.vue'
import TeamsTestResultModal from '~/components/feature/TeamsTestResultModal.vue'
import type { AccountRole, Member, QaItem, TeamsTestResult } from '~/types/api'

/** 팀원 관리 표 (관리자 전용). 예전 관리 페이지의 팀원 탭. */
const membersApi = useMembers()
const qaApi = useQa()
const auth = useAuthStore()
const { t } = useI18n()
const { confirmDialog, alertDialog } = useAppDialog()

const members = ref<Member[]>([])
const qas = ref<QaItem[]>([])
const loading = ref(true)

const memberModalOpen = ref(false)
const memberModalMode = ref<'create' | 'edit'>('create')
const editTarget = ref<Member | null>(null)
const deleteOpen = ref(false)
const deleteTarget = ref<Member | null>(null)

const teamsTestOpen = ref(false)
const teamsTestLoading = ref(false)
const teamsTestResult = ref<TeamsTestResult | null>(null)
const teamsTestTarget = ref<Member | null>(null)

async function load() {
  loading.value = true
  try {
    const [m, q] = await Promise.all([membersApi.list(), qaApi.list()])
    members.value = m
    qas.value = q
  } finally {
    loading.value = false
  }
}
if (import.meta.client) onMounted(load)

function memberAssignedCount(memberId: number) {
  return qas.value.filter((q) => q.assignee1?.id === memberId || q.assignee2?.id === memberId).length
}

async function runTeamsTest(m: Member) {
  teamsTestTarget.value = m
  teamsTestResult.value = null
  teamsTestLoading.value = true
  teamsTestOpen.value = true
  try {
    teamsTestResult.value = await membersApi.teamsTest(m.id)
  } catch (e: unknown) {
    teamsTestResult.value = {
      success: false,
      errorMessage: e instanceof Error ? e.message : t('admin.teams.requestFailed'),
      configOk: false,
      notifyEnabled: false,
      aadMapped: false,
      chatOk: false,
      sent: false,
    }
  } finally {
    teamsTestLoading.value = false
  }
}

function openCreate() {
  editTarget.value = null
  memberModalMode.value = 'create'
  memberModalOpen.value = true
}
function openEdit(m: Member) {
  editTarget.value = m
  memberModalMode.value = 'edit'
  memberModalOpen.value = true
}
function onCreated(m: Member) {
  members.value.push(m)
}
function onUpdated(m: Member) {
  members.value = members.value.map((x) => (x.id === m.id ? m : x))
}
function openDelete(m: Member) {
  if (m.id === auth.user?.id) {
    void alertDialog({ message: t('admin.members.cannotDeleteSelf') })
    return
  }
  deleteTarget.value = m
  deleteOpen.value = true
}
async function confirmDelete() {
  if (!deleteTarget.value) return
  await membersApi.remove(deleteTarget.value.id)
  members.value = members.value.filter((x) => x.id !== deleteTarget.value!.id)
  deleteOpen.value = false
  deleteTarget.value = null
}
async function resetPassword(m: Member) {
  if (!(await confirmDialog({ message: t('admin.members.resetPasswordConfirm', { name: m.name, username: m.username }) }))) return
  try {
    await membersApi.resetPassword(m.id)
    await alertDialog({ message: t('admin.members.resetPasswordDone', { name: m.name }) })
  } catch (e: unknown) {
    await alertDialog({ message: e instanceof Error ? e.message : t('admin.members.resetPasswordFailed'), danger: true })
  }
}
/** 계정 권한(ADMIN/MEMBER) 변경. 실패/취소 시 select 를 원래 값으로 되돌린다. */
async function onAccountRoleChange(m: Member, e: Event) {
  const select = e.target as HTMLSelectElement
  const next = select.value as AccountRole
  const prev = m.accountRole ?? 'MEMBER'
  if (next === prev) return
  const roleLabel = t(`common.accountRole.${next.toLowerCase()}`)
  if (!(await confirmDialog({ message: t('admin.members.accountRole.confirm', { name: m.name, role: roleLabel }) }))) {
    select.value = prev
    return
  }
  try {
    const updated = await membersApi.updateAccountRole(m.id, next)
    members.value = members.value.map((x) => (x.id === m.id ? updated : x))
  } catch (err: any) {
    select.value = prev
    await alertDialog({ message: err?.data?.message ?? t('admin.members.accountRole.failed'), danger: true })
  }
}

const actionBtn = 'flex h-8 w-8 items-center justify-center rounded-lg text-slate-500 transition-colors dark:text-slate-400'
</script>

<template>
  <div>
    <div class="mb-3 flex items-center justify-end">
      <button
        type="button"
        class="inline-flex items-center gap-1.5 whitespace-nowrap rounded-lg bg-emerald-600 px-3 py-2 text-sm font-medium text-white hover:bg-emerald-700"
        @click="openCreate"
      >
        <UserPlus class="h-4 w-4" />
        {{ $t('admin.members.addMember') }}
      </button>
    </div>

    <div class="overflow-hidden rounded-xl border border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-900">
      <div class="overflow-x-auto">
        <table v-if="loading" class="w-full">
          <thead class="border-b border-slate-200 bg-slate-50 dark:border-slate-800 dark:bg-slate-800/50">
            <tr>
              <th v-for="i in 5" :key="i" class="px-4 py-3"><div class="h-3 w-16 animate-pulse rounded bg-slate-200 dark:bg-slate-800" /></th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-100 dark:divide-slate-800">
            <tr v-for="r in 4" :key="r">
              <td v-for="c in 5" :key="c" class="px-4 py-4"><div class="h-4 animate-pulse rounded bg-slate-100 dark:bg-slate-800/60" :style="{ width: `${40 + ((r * c) % 5) * 15}%` }" /></td>
            </tr>
          </tbody>
        </table>
        <table v-else class="w-full text-left text-sm">
          <thead class="border-b border-slate-200 bg-slate-50 dark:border-slate-800 dark:bg-slate-800/50">
            <tr>
              <th class="w-12 whitespace-nowrap px-4 py-3 text-xs font-semibold text-slate-500 dark:text-slate-400">#</th>
              <th class="whitespace-nowrap px-4 py-3 text-xs font-semibold text-slate-500 dark:text-slate-400">{{ $t('admin.members.fields.name') }}</th>
              <th class="w-32 whitespace-nowrap px-4 py-3 text-xs font-semibold text-slate-500 dark:text-slate-400">{{ $t('admin.members.fields.username') }}</th>
              <th class="w-40 whitespace-nowrap px-4 py-3 text-xs font-semibold text-slate-500 dark:text-slate-400">{{ $t('admin.members.fields.role') }}</th>
              <th class="w-32 whitespace-nowrap px-4 py-3 text-xs font-semibold text-slate-500 dark:text-slate-400">{{ $t('admin.members.accountRole.header') }}</th>
              <th class="w-24 whitespace-nowrap px-4 py-3 text-xs font-semibold text-slate-500 dark:text-slate-400">{{ $t('admin.members.assignedHeader') }}</th>
              <th class="w-40 whitespace-nowrap px-4 py-3 text-xs font-semibold text-slate-500 dark:text-slate-400">{{ $t('admin.table.actions') }}</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-100 dark:divide-slate-800">
            <tr v-for="(m, index) in members" :key="m.id" class="transition-colors hover:bg-slate-50 dark:hover:bg-slate-800/60">
              <td class="px-4 py-3 text-slate-400 dark:text-slate-500">{{ index + 1 }}</td>
              <td class="px-4 py-3">
                <div class="flex items-center gap-3">
                  <img v-if="m.avatarUrl" :src="m.avatarUrl" :alt="m.name" class="h-8 w-8 rounded-full bg-slate-100 object-cover dark:bg-slate-800" />
                  <div v-else class="flex h-8 w-8 items-center justify-center rounded-full bg-emerald-100 text-sm font-medium text-emerald-600 dark:bg-emerald-500/20 dark:text-emerald-400">{{ m.name.charAt(0) }}</div>
                  <span class="whitespace-nowrap font-medium text-slate-800 dark:text-slate-100">{{ m.name }}</span>
                </div>
              </td>
              <td class="whitespace-nowrap px-4 py-3 text-xs text-slate-500 dark:text-slate-400">{{ m.username }}</td>
              <td class="whitespace-nowrap px-4 py-3 text-slate-600 dark:text-slate-300">{{ m.role ?? '-' }}</td>
              <td class="px-4 py-3">
                <!-- 자기 자신의 권한은 변경 불가(잠금 방지) — 배지로만 표시 -->
                <span
                  v-if="m.id === auth.user?.id"
                  :class="[
                    'inline-flex items-center gap-1 rounded-full px-2.5 py-1 text-xs font-medium',
                    (m.accountRole ?? 'MEMBER') === 'ADMIN'
                      ? 'bg-emerald-50 text-emerald-600 dark:bg-emerald-500/10 dark:text-emerald-400'
                      : 'bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-300',
                  ]"
                  :title="$t('admin.members.accountRole.selfLocked')"
                >
                  <ShieldCheck v-if="(m.accountRole ?? 'MEMBER') === 'ADMIN'" class="h-3 w-3" />
                  {{ $t(`common.accountRole.${(m.accountRole ?? 'MEMBER').toLowerCase()}`) }}
                </span>
                <select
                  v-else
                  :value="m.accountRole ?? 'MEMBER'"
                  class="rounded-md border border-slate-200 bg-white px-2 py-1 text-xs text-slate-700 focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-200"
                  @change="onAccountRoleChange(m, $event)"
                >
                  <option value="ADMIN">{{ $t('common.accountRole.admin') }}</option>
                  <option value="MEMBER">{{ $t('common.accountRole.member') }}</option>
                </select>
              </td>
              <td class="px-4 py-3">
                <span class="whitespace-nowrap rounded-full bg-slate-100 px-2 py-1 text-xs font-medium text-slate-600 dark:bg-slate-800 dark:text-slate-300">
                  {{ $t('admin.members.assignedCount', memberAssignedCount(m.id)) }}
                </span>
              </td>
              <td class="px-4 py-3">
                <div class="flex items-center gap-2">
                  <button type="button" :class="[actionBtn, 'hover:bg-sky-50 hover:text-sky-500 dark:hover:bg-sky-500/10 dark:hover:text-sky-400']" :title="$t('admin.teams.testTitle')" @click="runTeamsTest(m)">
                    <Send class="h-4 w-4" />
                  </button>
                  <button type="button" :class="[actionBtn, 'hover:bg-amber-50 hover:text-amber-500 dark:hover:bg-amber-500/10 dark:hover:text-amber-400']" :title="$t('admin.members.resetPasswordTooltip')" @click="resetPassword(m)">
                    <KeyRound class="h-4 w-4" />
                  </button>
                  <button type="button" :class="[actionBtn, 'hover:bg-emerald-50 hover:text-emerald-500 dark:hover:bg-emerald-500/10 dark:hover:text-emerald-400']" :title="$t('common.actions.edit')" @click="openEdit(m)">
                    <Edit3 class="h-4 w-4" />
                  </button>
                  <button type="button" :class="[actionBtn, 'hover:bg-red-50 hover:text-red-500 dark:hover:bg-red-500/10 dark:hover:text-red-400']" :title="$t('common.actions.delete')" @click="openDelete(m)">
                    <Trash2 class="h-4 w-4" />
                  </button>
                </div>
              </td>
            </tr>
            <tr v-if="members.length === 0">
              <td colspan="7" class="px-4 py-8 text-center text-sm text-slate-400 dark:text-slate-500">{{ $t('admin.members.empty') }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <MemberModal
      :open="memberModalOpen"
      :mode="memberModalMode"
      :member="editTarget"
      @close="memberModalOpen = false"
      @created="onCreated"
      @updated="onUpdated"
    />
    <DeleteConfirmModal
      :open="deleteOpen"
      :title="deleteTarget ? $t('admin.members.deleteTitle', { name: deleteTarget.name }) : undefined"
      :message="$t('admin.members.deleteMessage')"
      @confirm="confirmDelete"
      @cancel="deleteOpen = false; deleteTarget = null"
    />
    <TeamsTestResultModal
      :open="teamsTestOpen"
      :loading="teamsTestLoading"
      :result="teamsTestResult"
      :member-name="teamsTestTarget?.name"
      @close="teamsTestOpen = false"
    />
  </div>
</template>
