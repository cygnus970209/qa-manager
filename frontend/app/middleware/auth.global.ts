/**
 * 인증 가드. SSR + 클라이언트 양쪽에서 동작한다.
 * - /auth/* 는 비인증 허용
 * - 그 외 경로는 HttpOnly 쿠키 기반으로 /api/me 검증
 */
export default defineNuxtRouteMiddleware(async (to) => {
  const auth = useAuthStore()

  if (!auth.initialized) {
    await auth.bootstrap()
  }

  const isAuthRoute = to.path.startsWith('/auth/')

  // 서버에서 access 토큰이 만료돼 아직 인증을 확정하지 못한 상태(initialized=false).
  // refresh 쿠키가 있으면 토큰 갱신은 클라이언트(브라우저가 쿠키/Set-Cookie 자동 처리)에서
  // 정상 동작하므로, 여기서 로그인으로 보내지 않고 하이드레이션 후 복구에 맡긴다.
  // (qam_refresh_token: AuthCookieUtil.REFRESH_COOKIE 와 동일)
  if (import.meta.server && !auth.initialized && !isAuthRoute) {
    if (useCookie('qam_refresh_token').value) return
  }

  if (!auth.isAuthenticated && !isAuthRoute) {
    return navigateTo({ path: '/auth/login', query: { redirect: to.fullPath } })
  }
  if (auth.isAuthenticated && isAuthRoute) {
    return navigateTo('/')
  }
})
