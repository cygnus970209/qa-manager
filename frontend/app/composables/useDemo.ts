export interface DemoAccount {
  label: string
  username: string
  password: string
}

/**
 * "라벨|아이디|비밀번호" 를 세미콜론(;)으로 구분한 문자열을 계정 배열로 파싱한다.
 * - 3필드: 라벨|아이디|비밀번호
 * - 2필드: 아이디|비밀번호 (라벨은 아이디로 대체)
 * 비밀번호에 ; 또는 | 가 들어가면 깨지므로 데모용 단순 비밀번호를 사용한다.
 */
function parseDemoAccounts(raw: unknown): DemoAccount[] {
  if (typeof raw !== 'string' || !raw.trim()) return []
  return raw
    .split(';')
    .map((chunk) => chunk.trim())
    .filter(Boolean)
    .map((chunk): DemoAccount | null => {
      const parts = chunk.split('|').map((p) => p.trim())
      if (parts.length >= 3 && parts[1]) {
        return { label: parts[0] || parts[1], username: parts[1], password: parts[2] ?? '' }
      }
      if (parts.length === 2 && parts[0]) {
        return { label: parts[0], username: parts[0], password: parts[1] ?? '' }
      }
      return null
    })
    .filter((a): a is DemoAccount => a !== null)
}

/**
 * 데모 모드 상태와 노출할 데모 계정 목록을 제공한다.
 * NUXT_PUBLIC_DEMO_MODE / NUXT_PUBLIC_DEMO_ACCOUNTS 환경변수로 제어한다.
 */
export function useDemo() {
  const config = useRuntimeConfig()
  // 환경변수 오버라이드 시 boolean/문자열 어느 쪽으로 들어와도 안전하게 처리.
  const enabled = computed(() => {
    const v = config.public.demoMode as unknown
    return v === true || v === 'true' || v === 1 || v === '1'
  })
  const accounts = computed(() => parseDemoAccounts(config.public.demoAccounts))
  return { enabled, accounts }
}
