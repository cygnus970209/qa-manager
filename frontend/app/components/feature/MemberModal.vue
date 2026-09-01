<script setup lang="ts">
import AppDialog from '~/components/base/AppDialog.vue'
import type { Member } from '~/types/api'

const props = defineProps<{
  open: boolean
  mode: 'create' | 'edit'
  member?: Member | null
}>()
const emit = defineEmits<{
  close: []
  created: [member: Member]
  updated: [member: Member]
}>()

const membersApi = useMembers()
const { t } = useI18n()

const form = reactive({
  name: '',
  username: '',
  password: '',
  role: '',
  avatarUrl: '',
})
const error = ref<string | null>(null)
const submitting = ref(false)

watch(() => props.open, (v) => {
  if (!v) return
  error.value = null
  if (props.mode === 'edit' && props.member) {
    form.name = props.member.name
    form.username = props.member.username
    form.password = ''
    form.role = props.member.role ?? ''
    form.avatarUrl = props.member.avatarUrl ?? ''
  } else {
    form.name = ''
    form.username = ''
    form.password = ''
    form.role = ''
    form.avatarUrl = ''
  }
})

async function onSubmit() {
  error.value = null
  if (!form.name.trim()) { error.value = t('admin.members.nameRequired'); return }
  if (props.mode === 'create' && !form.username.trim()) { error.value = t('admin.members.usernameRequired'); return }
  if (props.mode === 'create' && !form.password) { error.value = t('admin.members.passwordRequired'); return }

  submitting.value = true
  try {
    if (props.mode === 'create') {
      const created = await membersApi.create({
        username: form.username.trim(),
        password: form.password,
        name: form.name.trim(),
        role: form.role.trim() || undefined,
        avatarUrl: form.avatarUrl.trim() || undefined,
      })
      emit('created', created)
    } else if (props.member) {
      const updated = await membersApi.update(props.member.id, {
        name: form.name.trim(),
        role: form.role.trim() || undefined,
        avatarUrl: form.avatarUrl.trim() || undefined,
      })
      emit('updated', updated)
    }
    emit('close')
  } catch (e: any) {
    error.value = e?.data?.message ?? t('admin.members.saveFailed')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <AppDialog :open="open" :title="mode === 'create' ? $t('admin.members.addMember') : $t('admin.members.editMember')" max-width="max-w-sm" @close="emit('close')">
    <form id="member-form" class="space-y-4" @submit.prevent="onSubmit">
      <p v-if="error" class="rounded-lg bg-red-50 px-3 py-2 text-xs text-red-600 dark:bg-red-500/10 dark:text-red-400">{{ error }}</p>

      <label class="block">
        <span class="block text-xs font-semibold text-slate-600 dark:text-slate-300">{{ $t('admin.members.fields.name') }}</span>
        <input
          v-model="form.name"
          type="text"
          :placeholder="$t('admin.members.namePlaceholder')"
          maxlength="50"
          class="mt-1.5 w-full rounded-lg border border-slate-200 px-3 py-2 text-sm focus:border-emerald-300 focus:outline-none focus:ring-2 focus:ring-emerald-100 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100 dark:placeholder-slate-500 dark:focus:ring-emerald-500/20"
        />
      </label>

      <label class="block">
        <span class="block text-xs font-semibold text-slate-600 dark:text-slate-300">{{ $t('admin.members.fields.username') }}</span>
        <input
          v-model="form.username"
          type="text"
          :placeholder="$t('admin.members.usernamePlaceholder')"
          maxlength="50"
          :disabled="mode === 'edit'"
          :class="[
            'mt-1.5 w-full rounded-lg border border-slate-200 px-3 py-2 text-sm dark:border-slate-700 dark:placeholder-slate-500',
            mode === 'edit'
              ? 'cursor-not-allowed bg-slate-50 text-slate-400 dark:bg-slate-800/60 dark:text-slate-500'
              : 'focus:border-emerald-300 focus:outline-none focus:ring-2 focus:ring-emerald-100 dark:bg-slate-900 dark:text-slate-100 dark:focus:ring-emerald-500/20',
          ]"
        />
      </label>

      <label v-if="mode === 'create'" class="block">
        <span class="block text-xs font-semibold text-slate-600 dark:text-slate-300">{{ $t('admin.members.fields.password') }}</span>
        <input
          v-model="form.password"
          type="password"
          placeholder="••••"
          minlength="4"
          maxlength="100"
          class="mt-1.5 w-full rounded-lg border border-slate-200 px-3 py-2 text-sm focus:border-emerald-300 focus:outline-none focus:ring-2 focus:ring-emerald-100 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100 dark:placeholder-slate-500 dark:focus:ring-emerald-500/20"
        />
      </label>

      <label class="block">
        <span class="block text-xs font-semibold text-slate-600 dark:text-slate-300">{{ $t('admin.members.fields.role') }}</span>
        <input
          v-model="form.role"
          type="text"
          :placeholder="$t('admin.members.rolePlaceholder')"
          maxlength="50"
          class="mt-1.5 w-full rounded-lg border border-slate-200 px-3 py-2 text-sm focus:border-emerald-300 focus:outline-none focus:ring-2 focus:ring-emerald-100 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100 dark:placeholder-slate-500 dark:focus:ring-emerald-500/20"
        />
      </label>

      <label class="block">
        <span class="block text-xs font-semibold text-slate-600 dark:text-slate-300">{{ $t('admin.members.fields.avatarUrl') }} <span class="font-normal text-slate-400 dark:text-slate-500">{{ $t('admin.members.fields.optional') }}</span></span>
        <input
          v-model="form.avatarUrl"
          type="url"
          maxlength="500"
          class="mt-1.5 w-full rounded-lg border border-slate-200 px-3 py-2 text-sm focus:border-emerald-300 focus:outline-none focus:ring-2 focus:ring-emerald-100 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100 dark:placeholder-slate-500 dark:focus:ring-emerald-500/20"
        />
      </label>
    </form>

    <template #footer>
      <button
        type="button"
        class="flex-1 rounded-lg bg-slate-100 px-4 py-2 text-sm font-medium text-slate-600 hover:bg-slate-200 dark:bg-slate-800 dark:text-slate-300 dark:hover:bg-slate-700"
        @click="emit('close')"
      >{{ $t('common.actions.cancel') }}</button>
      <button
        type="submit"
        form="member-form"
        :disabled="submitting"
        class="flex-1 rounded-lg bg-emerald-600 px-4 py-2 text-sm font-medium text-white hover:bg-emerald-700 disabled:opacity-60"
      >{{ submitting ? $t('common.state.saving') : (mode === 'create' ? $t('common.actions.add') : $t('common.actions.save')) }}</button>
    </template>
  </AppDialog>
</template>
