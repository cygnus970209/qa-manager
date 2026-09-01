import { createSeed } from '~/demo/seed'
import { getDemoDb } from '~/demo/db'

export interface DemoAccount {
  label: string
  username: string
  password: string
}

/** 호출 시점에 i18n 에 lazy 접근. nuxt 컨텍스트 밖(테스트 등)에서는 한국어 폴백. */
function tr(key: string, fallback: string): string {
  try {
    const { $i18n } = useNuxtApp() as any
    if ($i18n?.t) return $i18n.t(key)
  } catch { /* nuxt 컨텍스트 밖 */ }
  return fallback
}

/**
 * 데모 모드 상태 / 로그인 계정 / 초기화를 제공한다.
 * - enabled: DEMO_BUILD 빌드에서 켜짐(runtimeConfig.public.demoMode).
 * - accounts: 로그인 화면에 노출할 시드 계정(전부 비밀번호 1234).
 * - reset: 이 브라우저의 localStorage 데모 데이터를 시드 상태로 되돌린다.
 */
export function useDemo() {
  const config = useRuntimeConfig()
  const enabled = computed(() => config.public.demoMode === true)

  const accounts = computed<DemoAccount[]>(() => {
    if (!enabled.value) return []
    return createSeed().members.map((m) => ({
      label: [m.name, m.role].filter(Boolean).join(' · ') + (m.otpEnabled ? ' ' + tr('demo.accounts.otpTag', '(이메일 OTP 체험)') : ''),
      username: m.username,
      password: m.password,
    }))
  })

  function reset() {
    if (!import.meta.client) return
    getDemoDb().reset()
    window.location.reload()
  }

  return { enabled, accounts, reset }
}
