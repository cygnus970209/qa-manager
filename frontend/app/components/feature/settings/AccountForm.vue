<script setup lang="ts">
import { UserRound } from '@lucide/vue'

/** 내 계정 — 프로필 이미지·이름·비밀번호 변경 (예전 ProfileModal 의 내용) */
const { t } = useI18n()
const auth = useAuthStore()
const upload = useUpload()

const form = reactive({
  name: auth.user?.name ?? '',
  currentPassword: '',
  newPassword: '',
  newPasswordConfirm: '',
})
const avatarFile = ref<File | null>(null)
const avatarPreview = ref<string | null>(auth.user?.avatarUrl ?? null)
const submitting = ref(false)
const error = ref<string | null>(null)
const success = ref<string | null>(null)

watch(() => auth.user, (u) => {
  if (!u) return
  if (!avatarFile.value) avatarPreview.value = u.avatarUrl ?? null
  if (!form.name) form.name = u.name
})

function onPickAvatar(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  if (!file.type.startsWith('image/')) {
    error.value = t('auth.profile.imageOnly')
    return
  }
  avatarFile.value = file
  const reader = new FileReader()
  reader.onload = () => { avatarPreview.value = reader.result as string }
  reader.readAsDataURL(file)
}
function clearAvatarSelection() {
  avatarFile.value = null
  avatarPreview.value = auth.user?.avatarUrl ?? null
  const input = document.getElementById('avatar-input') as HTMLInputElement | null
  if (input) input.value = ''
}

const wantsPasswordChange = computed(
  () => form.currentPassword.length > 0 || form.newPassword.length > 0 || form.newPasswordConfirm.length > 0,
)

async function onSubmit() {
  error.value = null
  success.value = null
  if (wantsPasswordChange.value) {
    if (!form.currentPassword || !form.newPassword) {
      error.value = t('auth.profile.passwordBothRequired')
      return
    }
    if (form.newPassword.length < 4) {
      error.value = t('auth.profile.passwordTooShort')
      return
    }
    if (form.newPassword !== form.newPasswordConfirm) {
      error.value = t('auth.profile.passwordMismatch')
      return
    }
  }
  submitting.value = true
  try {
    let nextAvatarUrl: string | undefined
    if (avatarFile.value) {
      nextAvatarUrl = await upload.uploadFile(avatarFile.value, 'avatar')
    }
    const nameChanged = form.name.trim() !== (auth.user?.name ?? '')
    const avatarChanged = nextAvatarUrl !== undefined
    if (nameChanged || avatarChanged) {
      await auth.updateProfile({
        ...(nameChanged ? { name: form.name.trim() } : {}),
        ...(avatarChanged ? { avatarUrl: nextAvatarUrl } : {}),
      })
    }
    if (wantsPasswordChange.value) {
      await auth.changeMyPassword({
        currentPassword: form.currentPassword,
        newPassword: form.newPassword,
      })
      form.currentPassword = ''
      form.newPassword = ''
      form.newPasswordConfirm = ''
    }
    success.value = t('auth.profile.saved')
    avatarFile.value = null
  } catch (e: any) {
    error.value = e?.data?.message ?? t('auth.profile.saveFailed')
  } finally {
    submitting.value = false
  }
}

const inputCls = 'mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100 dark:placeholder-slate-500'
const readonlyCls = 'mt-1 w-full cursor-not-allowed rounded-md border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-500 dark:border-slate-800 dark:bg-slate-800/50 dark:text-slate-400'
const labelCls = 'block text-xs font-medium text-slate-600 dark:text-slate-300'
</script>

<template>
  <form class="max-w-xl space-y-6" @submit.prevent="onSubmit">
    <!-- 프로필 카드 -->
    <div class="flex items-center gap-5 rounded-xl border border-slate-200 bg-white p-5 dark:border-slate-800 dark:bg-slate-900">
      <div class="flex h-20 w-20 shrink-0 items-center justify-center overflow-hidden rounded-full bg-slate-100 dark:bg-slate-800">
        <img v-if="avatarPreview" :src="avatarPreview" :alt="$t('auth.profile.avatarPreviewAlt')" class="h-full w-full object-cover" />
        <UserRound v-else class="h-9 w-9 text-slate-400 dark:text-slate-500" />
      </div>
      <div class="min-w-0 flex-1">
        <p class="truncate text-base font-semibold text-slate-800 dark:text-slate-100">{{ auth.user?.name }}</p>
        <p class="mt-0.5 text-xs text-slate-500 dark:text-slate-400">
          {{ auth.user?.role ?? $t('auth.profile.roleUnassigned') }}
          <template v-if="auth.user?.accountRole"> · {{ $t(`common.accountRole.${auth.user.accountRole.toLowerCase()}`) }}</template>
        </p>
        <div class="mt-3 flex flex-wrap items-center gap-2">
          <label class="inline-flex cursor-pointer items-center rounded-md border border-slate-200 px-3 py-1.5 text-xs text-slate-700 hover:bg-slate-50 dark:border-slate-800 dark:text-slate-200 dark:hover:bg-slate-800/60">
            {{ $t('auth.profile.chooseImage') }}
            <input id="avatar-input" type="file" accept="image/*" class="hidden" @change="onPickAvatar" />
          </label>
          <button
            v-if="avatarFile"
            type="button"
            class="text-xs text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-200"
            @click="clearAvatarSelection"
          >{{ $t('common.actions.cancel') }}</button>
          <span class="text-[11px] text-slate-400 dark:text-slate-500">{{ avatarFile ? avatarFile.name : $t('auth.profile.uploadOnSave') }}</span>
        </div>
      </div>
    </div>

    <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
      <label class="block">
        <span :class="labelCls">{{ $t('auth.profile.username') }}</span>
        <input :value="auth.user?.username ?? ''" type="text" disabled :class="readonlyCls" />
      </label>
      <label class="block">
        <span :class="labelCls">{{ $t('auth.profile.name') }}</span>
        <input v-model="form.name" type="text" required maxlength="50" :class="inputCls" />
      </label>
      <label class="block">
        <span :class="labelCls">{{ $t('auth.profile.role') }}</span>
        <input :value="auth.user?.role ?? $t('auth.profile.roleUnassigned')" type="text" disabled :class="readonlyCls" />
        <p class="mt-1 text-[11px] text-slate-400 dark:text-slate-500">{{ $t('auth.profile.roleHint') }}</p>
      </label>
    </div>

    <fieldset class="rounded-xl border border-slate-200 p-4 dark:border-slate-800">
      <legend class="px-1 text-xs font-medium text-slate-600 dark:text-slate-300">
        {{ $t('auth.profile.changePassword') }} <span class="text-slate-400 dark:text-slate-500">{{ $t('auth.profile.optional') }}</span>
      </legend>
      <div class="grid grid-cols-1 gap-3 sm:grid-cols-3">
        <label class="block">
          <span :class="labelCls">{{ $t('auth.profile.currentPassword') }}</span>
          <input v-model="form.currentPassword" type="password" autocomplete="current-password" :class="inputCls" />
        </label>
        <label class="block">
          <span :class="labelCls">{{ $t('auth.profile.newPassword') }}</span>
          <input v-model="form.newPassword" type="password" autocomplete="new-password" minlength="4" maxlength="100" :class="inputCls" />
        </label>
        <label class="block">
          <span :class="labelCls">{{ $t('auth.profile.newPasswordConfirm') }}</span>
          <input v-model="form.newPasswordConfirm" type="password" autocomplete="new-password" :class="inputCls" />
        </label>
      </div>
    </fieldset>

    <p v-if="error" class="rounded bg-red-50 px-3 py-2 text-xs text-red-700 dark:bg-red-500/10 dark:text-red-300">{{ error }}</p>
    <p v-if="success" class="rounded bg-emerald-50 px-3 py-2 text-xs text-emerald-700 dark:bg-emerald-500/10 dark:text-emerald-400">{{ success }}</p>

    <div class="flex justify-end">
      <button
        type="submit"
        :disabled="submitting"
        class="rounded-lg bg-emerald-600 px-4 py-2 text-sm font-medium text-white hover:bg-emerald-700 disabled:opacity-60"
      >
        {{ submitting ? $t('common.state.saving') : $t('common.actions.save') }}
      </button>
    </div>
  </form>
</template>
