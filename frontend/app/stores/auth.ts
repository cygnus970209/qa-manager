import { defineStore } from 'pinia'
import type {
  ChangeMyPasswordRequest,
  LoginRequest,
  LoginResponse,
  Me,
  UpdateMeRequest,
} from '~/types/api'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<Me | null>(null)
  const initialized = ref(false)

  const isAuthenticated = computed(() => user.value !== null)

  async function login(req: LoginRequest) {
    const api = useApi()
    const res = await api<LoginResponse>('/api/auth/login', {
      method: 'POST',
      body: req,
    })
    user.value = res.user
    initialized.value = true
    return res
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
    logout,
    fetchMe,
    bootstrap,
    updateProfile,
    changeMyPassword,
  }
})
