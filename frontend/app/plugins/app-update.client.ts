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
