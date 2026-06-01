/**
 * 인증 가드. SSR + 클라이언트 양쪽에서 동작한다.
 * - /auth/* 는 비인증 허용
 * - 그 외 경로는 HttpOnly 쿠키 기반으로 /api/me 검증
 */
export default defineNuxtRouteMiddleware(async (to) => {
  const auth = useAuthStore()
  const nuxtApp = useNuxtApp()

  // 초기 하이드레이션 중인 클라이언트에서는 인증 확정용 네트워크 호출(bootstrap)을 하지 않는다.
  // 서버 렌더와 클라이언트 첫 렌더를 일치시켜 navbar 등의 하이드레이션 미스매치를 방지.
  // 실제 복구(refresh)는 plugins/auth-recover.client.ts 가 마운트 직후 수행한다.
  const hydrating = import.meta.client && nuxtApp.isHydrating

  if (!auth.initialized && !hydrating) {
    await auth.bootstrap()
  }

  const isAuthRoute = to.path.startsWith('/auth/')

  // 아직 인증을 확정하지 못한 상태(서버에서 access 만료 / 클라이언트 하이드레이션 중):
  // refresh 쿠키가 있으면 토큰 갱신은 클라이언트에서 정상 동작하므로,
  // 여기서 로그인으로 보내지 않고 복구에 맡긴다. (qam_refresh_token = AuthCookieUtil.REFRESH_COOKIE)
  if (!auth.initialized && !isAuthRoute) {
    if (hydrating || (import.meta.server && useCookie('qam_refresh_token').value)) return
  }

  if (!auth.isAuthenticated && !isAuthRoute) {
    return navigateTo({ path: '/auth/login', query: { redirect: to.fullPath } })
  }
  if (auth.isAuthenticated && isAuthRoute) {
    return navigateTo('/')
  }
})
