import { createSeed } from '~/demo/seed'
import { getDemoDb } from '~/demo/db'

export interface DemoAccount {
  label: string
  username: string
  password: string
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
      label: [m.name, m.role].filter(Boolean).join(' · '),
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
