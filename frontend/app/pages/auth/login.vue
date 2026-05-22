<script setup lang="ts">
import { ShieldCheck } from '@lucide/vue'
import type { ApiErrorBody } from '~/types/api'

definePageMeta({ layout: 'blank' })

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const username = ref('')
const password = ref('')
const submitting = ref(false)
const errorMessage = ref<string | null>(null)

function safeRedirect(raw: string | undefined): string {
  if (!raw) return '/'
  // 같은 사이트 내 경로만 허용. 외부 URL/프로토콜-상대 URL 차단.
  if (!raw.startsWith('/')) return '/'
  if (raw.startsWith('//')) return '/'
  if (raw.startsWith('/\\')) return '/'
  return raw
}

async function onSubmit() {
  errorMessage.value = null
  submitting.value = true
  try {
    await auth.login({ username: username.value, password: password.value })
    router.push(safeRedirect(route.query.redirect as string | undefined))
  } catch (e: any) {
    const body = e?.data as ApiErrorBody | undefined
    errorMessage.value = body?.message ?? '로그인에 실패했습니다.'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="flex min-h-screen items-center justify-center px-4">
    <div class="w-full max-w-sm rounded-xl border border-gray-200 bg-white p-8 shadow-sm">
      <div class="mb-6 flex items-center gap-2">
        <ShieldCheck class="h-6 w-6 text-emerald-600" />
        <h1 class="text-lg font-semibold tracking-tight">QA Manager 로그인</h1>
      </div>

      <form class="space-y-4" @submit.prevent="onSubmit">
        <label class="block">
          <span class="block text-xs font-medium text-gray-600">아이디</span>
          <input
            v-model="username"
            type="text"
            autocomplete="username"
            required
            class="mt-1 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500"
          />
        </label>
        <label class="block">
          <span class="block text-xs font-medium text-gray-600">비밀번호</span>
          <input
            v-model="password"
            type="password"
            autocomplete="current-password"
            required
            class="mt-1 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500"
          />
        </label>

        <p v-if="errorMessage" class="rounded-md bg-red-50 px-3 py-2 text-xs text-red-700">
          {{ errorMessage }}
        </p>

        <button
          type="submit"
          :disabled="submitting"
          class="w-full rounded-md bg-emerald-600 px-3 py-2 text-sm font-medium text-white hover:bg-emerald-700 disabled:opacity-60"
        >
          {{ submitting ? '로그인 중…' : '로그인' }}
        </button>
      </form>

    </div>
  </div>
</template>
