/**
 * 데스크톱 앱(별도 리포: qa-manager-desktop) 브리지 접근.
 * 데스크톱 셸이 웹뷰에 주입하는 `window.__QAM_DESKTOP__` 를 감싼다. 브라우저에서는 모든 호출이 null 을 돌려준다.
 * Tauri 등 특정 기술에 종속되지 않는 인터페이스로 유지할 것 (stores/notifications.ts 의 알림 브리지와 같은 객체).
 */
export type DesktopNotificationPermission = 'granted' | 'denied' | 'not_determined' | 'unsupported'

export interface DesktopInfo {
  version: string
  /** 'macos' | 'windows' | 'linux' */
  platform: string
}

interface DesktopBridge {
  getInfo?: () => Promise<DesktopInfo>
  /** 업데이트 확인. 결과(있음/없음/실패)는 셸이 네이티브 대화상자로 보여준다 */
  checkForUpdate?: () => Promise<void>
  getNotificationPermission?: () => Promise<DesktopNotificationPermission>
  /** 아직 묻지 않은 상태에서 권한 요청. 결과 상태를 돌려준다 */
  requestNotificationPermission?: () => Promise<DesktopNotificationPermission>
  /** OS 의 알림 설정 화면(이 앱 항목)을 연다 */
  openNotificationSettings?: () => Promise<void>
}

function bridge(): DesktopBridge | undefined {
  return import.meta.client ? (window as any).__QAM_DESKTOP__ : undefined
}

export function useDesktop() {
  const isDesktop = computed(() => !!bridge())

  /** 브리지의 메서드를 안전하게 호출. 브리지가 없거나 구버전이라 메서드가 없으면 null */
  async function call<T>(fn: (b: DesktopBridge) => Promise<T> | undefined): Promise<T | null> {
    const b = bridge()
    if (!b) return null
    try {
      const r = fn(b)
      return r === undefined ? null : await r
    } catch (e) {
      console.warn('[QAM desktop] 브리지 호출 실패:', e)
      return null
    }
  }

  return {
    isDesktop,
    /** 셸이 지원하는 기능인지 (구버전 앱 판별) */
    supports: (name: keyof DesktopBridge) => typeof bridge()?.[name] === 'function',
    getInfo: () => call((b) => b.getInfo?.()),
    version: async () => (await call((b) => b.getInfo?.()))?.version ?? null,
    checkForUpdate: () => call((b) => b.checkForUpdate?.()),
    getNotificationPermission: () => call((b) => b.getNotificationPermission?.()),
    requestNotificationPermission: () => call((b) => b.requestNotificationPermission?.()),
    openNotificationSettings: () => call((b) => b.openNotificationSettings?.()),
  }
}
