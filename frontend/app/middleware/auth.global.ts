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

  if (!auth.isAuthenticated && !isAuthRoute) {
    return navigateTo({ path: '/auth/login', query: { redirect: to.fullPath } })
  }
  if (auth.isAuthenticated && isAuthRoute) {
    return navigateTo('/')
  }
})
