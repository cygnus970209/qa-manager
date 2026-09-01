<script setup lang="ts">
import { ShieldCheck, Mail, ArrowLeft, Globe } from '@lucide/vue'
import type { ApiErrorBody } from '~/types/api'
import type { DemoAccount } from '~/composables/useDemo'

definePageMeta({ layout: 'blank' })

const route = useRoute()
const router = useRouter()
const { t, locale, setLocale } = useI18n()

// 로그인 화면(네비바 없음)에도 언어 전환 제공 — 데모 방문자의 첫 화면
async function toggleLocale() {
  await setLocale(locale.value === 'ko' ? 'en' : 'ko')
}
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
    errorMessage.value = body?.message ?? t('auth.login.failed')
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
      otpError.value = body.message ?? t('auth.otp.invalidCode')
    } else {
      // 세션 만료/시도 초과 → 처음부터 다시
      otpError.value = body?.message ?? t('auth.otp.failed')
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
      otpError.value = body.message ?? t('auth.otp.sessionExpired')
      backToCredentials()
    } else {
      otpError.value = body?.message ?? t('auth.otp.resendFailed')
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
  <div class="relative flex min-h-screen items-center justify-center px-4">
    <button
      type="button"
      class="absolute right-4 top-4 flex items-center gap-1.5 rounded-md border border-slate-200 bg-white px-2.5 py-1.5 text-xs font-medium text-slate-600 shadow-sm hover:bg-slate-50"
      :aria-label="$t('common.actions.language')"
      @click="toggleLocale"
    >
      <Globe class="h-3.5 w-3.5" />
      {{ locale === 'ko' ? 'English' : '한국어' }}
    </button>
    <div class="w-full max-w-sm rounded-xl border border-gray-200 bg-white p-8 shadow-sm">
      <div class="mb-6 flex items-center gap-2">
        <ShieldCheck class="h-6 w-6 text-emerald-600" />
        <h1 class="text-lg font-semibold tracking-tight">{{ $t('auth.login.title') }}</h1>
      </div>

      <!-- 1단계: 아이디/비밀번호 -->
      <form v-if="step === 'credentials'" class="space-y-4" @submit.prevent="onSubmitCredentials">
        <label class="block">
          <span class="block text-xs font-medium text-gray-600">{{ $t('auth.login.username') }}</span>
          <input
            v-model="username"
            type="text"
            autocomplete="username"
            required
            class="mt-1 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500"
          />
        </label>
        <label class="block">
          <span class="block text-xs font-medium text-gray-600">{{ $t('auth.login.password') }}</span>
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
          {{ submitting ? $t('auth.login.submitting') : $t('auth.login.submit') }}
        </button>
      </form>

      <!-- 데모 계정 안내 (데모 모드에서만 표시) -->
      <div
        v-if="step === 'credentials' && demoEnabled && demoAccounts.length"
        class="mt-6 border-t border-dashed border-gray-200 pt-4"
      >
        <p class="mb-2 text-xs font-medium text-gray-500">
          {{ $t('auth.login.demoAccounts') }} <span class="text-gray-400">{{ $t('auth.login.demoAccountsHint') }}</span>
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
            {{ $t('auth.otp.notice') }}<br />
            <i18n-t keypath="auth.otp.sentTo" scope="global">
              <template #email><span class="font-semibold">{{ maskedEmail }}</span></template>
            </i18n-t>
          </p>
        </div>

        <p v-if="demoEnabled" class="rounded-md bg-amber-50 px-3 py-2 text-xs text-amber-800">
          <i18n-t keypath="auth.otp.demoNotice" scope="global">
            <template #code><span class="font-mono font-semibold">123456</span></template>
          </i18n-t>
        </p>

        <label class="block">
          <span class="block text-xs font-medium text-gray-600">{{ $t('auth.otp.codeLabel') }}</span>
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
          <span v-if="remainingAttempts !== null"> {{ $t('auth.otp.remainingAttempts', remainingAttempts) }}</span>
        </p>

        <button
          type="submit"
          :disabled="submitting || code.length < 6"
          class="w-full rounded-md bg-emerald-600 px-3 py-2 text-sm font-medium text-white hover:bg-emerald-700 disabled:opacity-60"
        >
          {{ submitting ? $t('auth.otp.verifying') : $t('auth.otp.submit') }}
        </button>

        <div class="flex items-center justify-between text-xs">
          <button
            type="button"
            class="inline-flex items-center gap-1 text-gray-500 hover:text-gray-700"
            @click="backToCredentials"
          >
            <ArrowLeft class="h-3.5 w-3.5" /> {{ $t('auth.otp.backToStart') }}
          </button>
          <button
            type="button"
            :disabled="resendCooldown > 0"
            class="text-emerald-600 hover:text-emerald-700 disabled:text-gray-400"
            @click="onResend"
          >
            {{ resendCooldown > 0 ? $t('auth.otp.resendCooldown', { n: resendCooldown }) : $t('auth.otp.resend') }}
          </button>
        </div>
      </form>
    </div>
  </div>
</template>
