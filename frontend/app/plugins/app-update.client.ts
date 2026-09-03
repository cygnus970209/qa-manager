/**
 * 새 빌드 감지 상태 연결 + 데스크톱 앱 새로고침 단축키.
 * - Nuxt 가 새 빌드를 발견하면(`app:manifest:update`) 배너 상태를 켠다 (components/feature/AppUpdateBanner.vue).
 * - 데스크톱 앱(웹뷰)은 브라우저와 달리 ⌘R / F5 가 아무 일도 하지 않으므로 여기서 새로고침으로 연결한다.
 */
export default defineNuxtPlugin((nuxtApp) => {
  const { available } = useAppUpdate()
  nuxtApp.hook('app:manifest:update', () => {
    available.value = true
  })

  // 배포 뒤 옛 화면이 옛 해시의 청크를 요청하면 404 → "Failed to fetch dynamically imported module".
  // Nuxt 기본 동작은 다음 화면 이동 때 재로드지만 화면 이동이 아닌 곳(지연 컴포넌트 등)에서 나면 에러 페이지가 뜬다.
  // 청크 실패는 곧 새 빌드가 배포됐다는 뜻이므로 바로 새로고침한다. 무한 반복 방지로 1분에 한 번만.
  nuxtApp.hook('app:chunkError', () => {
    const KEY = 'qam-chunk-reload-at'
    const now = Date.now()
    let last = 0
    try { last = Number(sessionStorage.getItem(KEY) ?? 0) } catch { /* ignore */ }
    if (now - last < 60_000) return
    try { sessionStorage.setItem(KEY, String(now)) } catch { /* ignore */ }
    reloadNuxtApp({ path: window.location.pathname + window.location.search, persistState: false })
  })

  const desktop = useDesktop()
  if (desktop.isDesktop.value) {
    window.addEventListener('keydown', (e) => {
      const reloadKey = e.key === 'F5' || ((e.metaKey || e.ctrlKey) && !e.shiftKey && !e.altKey && e.key.toLowerCase() === 'r')
      if (!reloadKey) return
      e.preventDefault()
      window.location.reload()
    })
  }
})
