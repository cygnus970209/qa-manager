/**
 * 하이드레이션 직후, 서버에서 확정하지 못한 인증 상태를 클라이언트에서 복구한다.
 * - 미들웨어는 하이드레이션 중 bootstrap 을 건너뛰므로(미스매치 방지), 여기서 마운트 후 수행.
 * - 클라이언트의 /api/me 는 access 만료 시 api 플러그인이 자동 refresh 하므로 정상 복구된다.
 * - 복구 후에도 미인증이고 보호 경로면 로그인으로 보낸다.
 */
export default defineNuxtPlugin((nuxtApp) => {
  nuxtApp.hook('app:mounted', async () => {
    const auth = useAuthStore()
    if (auth.initialized) return

    await auth.bootstrap()

    if (!auth.isAuthenticated) {
      const route = useRoute()
      if (!route.path.startsWith('/auth/')) {
        await navigateTo({ path: '/auth/login', query: { redirect: route.fullPath } })
      }
    }
  })
})
