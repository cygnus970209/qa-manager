import { getAppManifest, reloadNuxtApp } from '#app'

/** Nuxt 의 buildAssetsURL 과 같은 경로 (`/_nuxt/builds/latest.json`) — 내부 모듈 대신 runtimeConfig 로 조립 */
function latestManifestUrl() {
  const { app } = useRuntimeConfig()
  const base = ((app.cdnURL as string | undefined) || app.baseURL || '/').replace(/\/+$/, '')
  const dir = (app.buildAssetsDir || '/_nuxt/').replace(/^\/+|\/+$/g, '')
  return `${base}/${dir}/builds/latest.json?${Date.now()}`
}

/**
 * 웹앱 새 빌드 감지.
 * Nuxt 는 내장 플러그인으로 `builds/latest.json` 을 주기적으로 확인해(`experimental.checkOutdatedBuildInterval`)
 * 새 빌드면 `app:manifest:update` 훅을 부르고, 다음 화면 이동 때 앱을 다시 불러온다.
 * 여기서는 그 상태를 화면에 보여주기 위해 들고 있고(배너), 배포 직후 바로 알 수 있도록
 * SSE 가 다시 붙었을 때(서버 재시작 = 배포일 가능성이 큼) 즉시 확인하는 `check()` 를 제공한다.
 * 데스크톱 앱은 새로고침 버튼이 없어 이 배너가 새 버전을 받는 유일한 안내다.
 */
export function useAppUpdate() {
  const available = useState<boolean>('app-update-available', () => false)

  /** 서버의 최신 빌드 id 가 현재 실행 중인 빌드와 다른지 확인. 다르면 available 을 켠다 */
  async function check() {
    if (!import.meta.client || available.value) return
    try {
      const current = await getAppManifest().catch(() => null)
      const latest = await $fetch<{ id?: string }>(latestManifestUrl())
      if (latest?.id && latest.id !== current?.id) {
        available.value = true
        // Nuxt 의 chunk-reload 플러그인도 같은 훅을 듣고 다음 화면 이동 때 다시 불러온다
        await useNuxtApp().callHook('app:manifest:update', latest as any)
      }
    } catch {
      // 데모(정적 호스팅)·구버전 서버처럼 manifest 가 없으면 조용히 넘어간다
    }
  }

  /** 지금 다시 불러오기 — 현재 화면 유지 */
  function reload() {
    reloadNuxtApp({ path: window.location.pathname + window.location.search, persistState: false })
  }

  return { available, check, reload }
}
