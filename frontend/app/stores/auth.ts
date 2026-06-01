import { defineStore } from 'pinia'
import type {
  AuthResponse,
  ChangeMyPasswordRequest,
  LoginRequest,
  Me,
  OtpResendRequest,
  OtpVerifyRequest,
  UpdateMeRequest,
} from '~/types/api'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<Me | null>(null)
  const initialized = ref(false)

  const isAuthenticated = computed(() => user.value !== null)

  /** 인증 응답을 처리해 인증 완료면 사용자 상태를 채운다. */
  function applyAuth(res: AuthResponse) {
    if (res.authenticated && res.user) {
      user.value = res.user
      initialized.value = true
    }
    return res
  }

  /** 1단계 로그인. otpRequired 면 user 는 채워지지 않고 challenge 정보가 반환된다. */
  async function login(req: LoginRequest) {
    const api = useApi()
    const res = await api<AuthResponse>('/api/auth/login', {
      method: 'POST',
      body: req,
    })
    return applyAuth(res)
  }

  /** 2단계 OTP 검증. 성공 시 인증 완료. */
  async function verifyOtp(req: OtpVerifyRequest) {
    const api = useApi()
    const res = await api<AuthResponse>('/api/auth/login/otp/verify', {
      method: 'POST',
      body: req,
    })
    return applyAuth(res)
  }

  /** OTP 재발송(쿨다운은 서버에서 429). */
  async function resendOtp(req: OtpResendRequest) {
    const api = useApi()
    return await api<AuthResponse>('/api/auth/login/otp/resend', {
      method: 'POST',
      body: req,
    })
  }

  async function fetchMe() {
    const api = useApi()
    try {
      user.value = await api<Me>('/api/me')
      initialized.value = true
    } catch {
      user.value = null
      // 서버에서 access 토큰 만료로 실패한 경우는 인증 상태를 확정짓지 않는다.
      // refresh(rotation) 는 쿠키를 자동 처리하는 클라이언트에서만 안전하게 동작하므로,
      // initialized 를 false 로 둬서 하이드레이션 직후 미들웨어 bootstrap 이 재시도하게 한다.
      if (import.meta.client) {
        initialized.value = true
      }
    }
  }

  async function logout() {
    const api = useApi()
    try {
      await api('/api/auth/logout', { method: 'POST' })
    } catch {
      // ignore — 쿠키는 백엔드가 만료, 실패해도 클라이언트 상태 초기화
    }
    user.value = null
  }

  /** 앱 시작 시 1회 실행. 쿠키가 있으면 /api/me 로 사용자 복구. SSR 에서도 안전. */
  async function bootstrap() {
    if (initialized.value) return
    await fetchMe()
  }

  async function updateProfile(req: UpdateMeRequest) {
    const api = useApi()
    user.value = await api<Me>('/api/me', { method: 'PATCH', body: req })
    return user.value
  }

  async function changeMyPassword(req: ChangeMyPasswordRequest) {
    const api = useApi()
    await api('/api/me/password', { method: 'POST', body: req })
  }

  return {
    user,
    initialized,
    isAuthenticated,
    login,
    verifyOtp,
    resendOtp,
    logout,
    fetchMe,
    bootstrap,
    updateProfile,
    changeMyPassword,
  }
})
