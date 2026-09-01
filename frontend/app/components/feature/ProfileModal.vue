<script setup lang="ts">
import { UserRound } from '@lucide/vue'
import AppDialog from '~/components/base/AppDialog.vue'

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ close: [] }>()

const { t } = useI18n()
const auth = useAuthStore()
const upload = useUpload()

const form = reactive({
  name: '',
  currentPassword: '',
  newPassword: '',
  newPasswordConfirm: '',
})

const avatarFile = ref<File | null>(null)
const avatarPreview = ref<string | null>(null)
const submitting = ref(false)
const error = ref<string | null>(null)
const success = ref<string | null>(null)

watch(() => props.open, (v) => {
  if (!v) return
  form.name = auth.user?.name ?? ''
  form.currentPassword = ''
  form.newPassword = ''
  form.newPasswordConfirm = ''
  avatarFile.value = null
  avatarPreview.value = auth.user?.avatarUrl ?? null
  error.value = null
  success.value = null
})

function onPickAvatar(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  if (!file.type.startsWith('image/')) {
    error.value = t('auth.profile.imageOnly')
    return
  }
  avatarFile.value = file
  // 로컬 미리보기
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
</script>

<template>
  <AppDialog :open="open" :title="$t('auth.profile.title')" @close="emit('close')">
    <form id="profile-form" class="space-y-5" @submit.prevent="onSubmit">
      <!-- 아바타 -->
      <div class="flex items-center gap-4">
        <div class="flex h-16 w-16 items-center justify-center overflow-hidden rounded-full bg-slate-100">
          <img v-if="avatarPreview" :src="avatarPreview" :alt="$t('auth.profile.avatarPreviewAlt')" class="h-full w-full object-cover" />
          <UserRound v-else class="h-8 w-8 text-slate-400" />
        </div>
        <div class="flex-1">
          <label class="inline-flex cursor-pointer items-center rounded-md border border-slate-200 px-3 py-1.5 text-xs text-slate-700 hover:bg-slate-50">
            {{ $t('auth.profile.chooseImage') }}
            <input
              id="avatar-input"
              type="file"
              accept="image/*"
              class="hidden"
              @change="onPickAvatar"
            />
          </label>
          <button
            v-if="avatarFile"
            type="button"
            class="ml-2 text-xs text-slate-500 hover:text-slate-700"
            @click="clearAvatarSelection"
          >{{ $t('common.actions.cancel') }}</button>
          <p class="mt-1 text-[11px] text-slate-400">
            {{ avatarFile ? avatarFile.name : $t('auth.profile.uploadOnSave') }}
          </p>
        </div>
      </div>

      <!-- 사용자명 (읽기전용) -->
      <label class="block">
        <span class="block text-xs font-medium text-slate-600">{{ $t('auth.profile.username') }}</span>
        <input
          :value="auth.user?.username ?? ''"
          type="text"
          disabled
          class="mt-1 w-full cursor-not-allowed rounded-md border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-500"
        />
      </label>

      <!-- 이름 -->
      <label class="block">
        <span class="block text-xs font-medium text-slate-600">{{ $t('auth.profile.name') }}</span>
        <input
          v-model="form.name"
          type="text"
          required
          maxlength="50"
          class="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500"
        />
      </label>

      <!-- 역할 (읽기전용) -->
      <label class="block">
        <span class="block text-xs font-medium text-slate-600">{{ $t('auth.profile.role') }}</span>
        <input
          :value="auth.user?.role ?? $t('auth.profile.roleUnassigned')"
          type="text"
          disabled
          class="mt-1 w-full cursor-not-allowed rounded-md border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-500"
        />
        <p class="mt-1 text-[11px] text-slate-400">{{ $t('auth.profile.roleHint') }}</p>
      </label>

      <!-- 비밀번호 변경 -->
      <fieldset class="space-y-3 rounded-md border border-slate-200 p-3">
        <legend class="px-1 text-xs font-medium text-slate-600">{{ $t('auth.profile.changePassword') }} <span class="text-slate-400">{{ $t('auth.profile.optional') }}</span></legend>
        <label class="block">
          <span class="block text-xs font-medium text-slate-600">{{ $t('auth.profile.currentPassword') }}</span>
          <input
            v-model="form.currentPassword"
            type="password"
            autocomplete="current-password"
            class="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500"
          />
        </label>
        <label class="block">
          <span class="block text-xs font-medium text-slate-600">{{ $t('auth.profile.newPassword') }}</span>
          <input
            v-model="form.newPassword"
            type="password"
            autocomplete="new-password"
            minlength="4"
            maxlength="100"
            class="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500"
          />
        </label>
        <label class="block">
          <span class="block text-xs font-medium text-slate-600">{{ $t('auth.profile.newPasswordConfirm') }}</span>
          <input
            v-model="form.newPasswordConfirm"
            type="password"
            autocomplete="new-password"
            class="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500"
          />
        </label>
      </fieldset>

      <p v-if="error" class="rounded bg-red-50 px-3 py-2 text-xs text-red-700">{{ error }}</p>
      <p v-if="success" class="rounded bg-emerald-50 px-3 py-2 text-xs text-emerald-700">{{ success }}</p>
    </form>
    <template #footer>
      <button
        type="button"
        class="rounded-md border border-slate-200 px-3 py-1.5 text-sm text-slate-600 hover:bg-slate-50"
        @click="emit('close')"
      >{{ $t('common.actions.close') }}</button>
      <button
        type="submit"
        form="profile-form"
        :disabled="submitting"
        class="rounded-md bg-emerald-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-emerald-700 disabled:opacity-60"
      >
        {{ submitting ? $t('common.state.saving') : $t('common.actions.save') }}
      </button>
    </template>
  </AppDialog>
</template>
