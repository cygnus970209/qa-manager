<script setup lang="ts">
import { ShieldCheck, Mail, ArrowLeft } from '@lucide/vue'
import type { ApiErrorBody } from '~/types/api'
import type { DemoAccount } from '~/composables/useDemo'

definePageMeta({ layout: 'blank' })

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const { enabled: demoEnabled, accounts: demoAccounts } = useDemo()

type Step = 'credentials' | 'otp'
const step = ref<Step>('credentials')

const username = ref('')
const password = ref('')
const submitting = ref(false)
const errorMessage = ref<string | null>(null)

// OTP 단계 상태
const challengeId = ref('')
const maskedEmail = ref('')
const code = ref('')
const otpError = ref<string | null>(null)
const remainingAttempts = ref<number | null>(null)
const resendCooldown = ref(0)
let cooldownTimer: ReturnType<typeof setInterval> | null = null

function safeRedirect(raw: string | undefined): string {
  if (!raw) return '/'
  // 같은 사이트 내 경로만 허용. 외부 URL/프로토콜-상대 URL 차단.
  if (!raw.startsWith('/')) return '/'
  if (raw.startsWith('//')) return '/'
  if (raw.startsWith('/\\')) return '/'
  return raw
}

function goAfterLogin() {
  router.push(safeRedirect(route.query.redirect as string | undefined))
}

function startCooldown(seconds: number) {
  resendCooldown.value = seconds
  if (cooldownTimer) clearInterval(cooldownTimer)
  cooldownTimer = setInterval(() => {
    resendCooldown.value -= 1
    if (resendCooldown.value <= 0 && cooldownTimer) {
      clearInterval(cooldownTimer)
      cooldownTimer = null
    }
  }, 1000)
}

onBeforeUnmount(() => {
  if (cooldownTimer) clearInterval(cooldownTimer)
})

async function onSubmitCredentials() {
  errorMessage.value = null
  submitting.value = true
  try {
    const res = await auth.login({ username: username.value, password: password.value })
    if (res.otpRequired) {
      // 2단계 진입
      challengeId.value = res.challengeId ?? ''
      maskedEmail.value = res.maskedEmail ?? ''
      code.value = ''
      otpError.value = null
      remainingAttempts.value = null
      step.value = 'otp'
      startCooldown(60)
    } else {
      goAfterLogin()
    }
  } catch (e: any) {
    const body = e?.data as ApiErrorBody | undefined
    errorMessage.value = body?.message ?? '로그인에 실패했습니다.'
  } finally {
    submitting.value = false
  }
}

async function onVerifyOtp() {
  otpError.value = null
  submitting.value = true
  try {
    await auth.verifyOtp({ challengeId: challengeId.value, code: code.value })
    goAfterLogin()
  } catch (e: any) {
    const body = e?.data as ApiErrorBody | undefined
    if (body?.code === 'OTP_INVALID') {
      const details = body.details as { remainingAttempts?: number } | undefined
      remainingAttempts.value = details?.remainingAttempts ?? null
      otpError.value = body.message ?? '인증 코드가 올바르지 않습니다.'
    } else {
      // 세션 만료/시도 초과 → 처음부터 다시
      otpError.value = body?.message ?? '인증에 실패했습니다. 다시 로그인해 주세요.'
      backToCredentials()
    }
  } finally {
    submitting.value = false
  }
}

async function onResend() {
  if (resendCooldown.value > 0) return
  otpError.value = null
  try {
    const res = await auth.resendOtp({ challengeId: challengeId.value })
    challengeId.value = res.challengeId ?? challengeId.value
    code.value = ''
    remainingAttempts.value = null
    startCooldown(60)
  } catch (e: any) {
    const body = e?.data as ApiErrorBody | undefined
    if (body?.code === 'UNAUTHORIZED') {
      otpError.value = body.message ?? '인증 세션이 만료되었습니다.'
      backToCredentials()
    } else {
      otpError.value = body?.message ?? '재전송에 실패했습니다.'
    }
  }
}

function backToCredentials() {
  step.value = 'credentials'
  code.value = ''
  challengeId.value = ''
  if (cooldownTimer) clearInterval(cooldownTimer)
  resendCooldown.value = 0
}

// 데모 계정 클릭 → 폼을 자동으로 채운 뒤 즉시 로그인 시도.
function loginAs(acc: DemoAccount) {
  if (submitting.value) return
  username.value = acc.username
  password.value = acc.password
  onSubmitCredentials()
}
</script>

<template>
  <div class="flex min-h-screen items-center justify-center px-4">
    <div class="w-full max-w-sm rounded-xl border border-gray-200 bg-white p-8 shadow-sm">
      <div class="mb-6 flex items-center gap-2">
        <ShieldCheck class="h-6 w-6 text-emerald-600" />
        <h1 class="text-lg font-semibold tracking-tight">QA Manager 로그인</h1>
      </div>

      <!-- 1단계: 아이디/비밀번호 -->
      <form v-if="step === 'credentials'" class="space-y-4" @submit.prevent="onSubmitCredentials">
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

      <!-- 데모 계정 안내 (데모 모드에서만 표시) -->
      <div
        v-if="step === 'credentials' && demoEnabled && demoAccounts.length"
        class="mt-6 border-t border-dashed border-gray-200 pt-4"
      >
        <p class="mb-2 text-xs font-medium text-gray-500">
          데모 계정 <span class="text-gray-400">(클릭하면 바로 로그인)</span>
        </p>
        <ul class="space-y-1.5">
          <li v-for="acc in demoAccounts" :key="acc.username">
            <button
              type="button"
              :disabled="submitting"
              class="flex w-full items-center justify-between gap-2 rounded-md border border-gray-200 bg-gray-50 px-3 py-2 text-left hover:border-emerald-300 hover:bg-emerald-50 disabled:opacity-60"
              @click="loginAs(acc)"
            >
              <span class="text-xs font-medium text-gray-700">{{ acc.label }}</span>
              <span class="font-mono text-[11px] text-gray-500">{{ acc.username }} / {{ acc.password }}</span>
            </button>
          </li>
        </ul>
      </div>

      <!-- 2단계: 이메일 OTP -->
      <form v-else class="space-y-4" @submit.prevent="onVerifyOtp">
        <div class="flex items-start gap-2.5 rounded-lg bg-emerald-50 px-3.5 py-3">
          <Mail class="mt-0.5 h-5 w-5 shrink-0 text-emerald-600" />
          <p class="text-xs text-emerald-800">
            보안을 위해 추가 인증이 필요합니다.<br />
            <span class="font-semibold">{{ maskedEmail }}</span> 로 보낸 6자리 인증 코드를 입력하세요.
          </p>
        </div>

        <label class="block">
          <span class="block text-xs font-medium text-gray-600">인증 코드</span>
          <input
            v-model="code"
            type="text"
            inputmode="numeric"
            autocomplete="one-time-code"
            maxlength="6"
            placeholder="000000"
            required
            class="mt-1 w-full rounded-md border border-gray-300 px-3 py-2 text-center text-lg tracking-[0.4em] focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500"
          />
        </label>

        <p v-if="otpError" class="rounded-md bg-red-50 px-3 py-2 text-xs text-red-700">
          {{ otpError }}
          <span v-if="remainingAttempts !== null"> (남은 시도 {{ remainingAttempts }}회)</span>
        </p>

        <button
          type="submit"
          :disabled="submitting || code.length < 6"
          class="w-full rounded-md bg-emerald-600 px-3 py-2 text-sm font-medium text-white hover:bg-emerald-700 disabled:opacity-60"
        >
          {{ submitting ? '확인 중…' : '인증 후 로그인' }}
        </button>

        <div class="flex items-center justify-between text-xs">
          <button
            type="button"
            class="inline-flex items-center gap-1 text-gray-500 hover:text-gray-700"
            @click="backToCredentials"
          >
            <ArrowLeft class="h-3.5 w-3.5" /> 처음으로
          </button>
          <button
            type="button"
            :disabled="resendCooldown > 0"
            class="text-emerald-600 hover:text-emerald-700 disabled:text-gray-400"
            @click="onResend"
          >
            {{ resendCooldown > 0 ? `재전송 (${resendCooldown}초)` : '코드 재전송' }}
          </button>
        </div>
      </form>
    </div>
  </div>
</template>
