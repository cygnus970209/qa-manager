/**
 * 인증된 $fetch 인스턴스를 nuxtApp 에 주입한다.
 * - 인증은 HttpOnly 쿠키로 자동 처리됨 (credentials: 'include')
 * - 401 응답 시 /api/auth/refresh 호출 → 실패 시 클라이언트에서 /auth/login 리다이렉트
 */
type ApiFetch = typeof $fetch

export default defineNuxtPlugin(() => {
  const config = useRuntimeConfig()
  const apiBase = config.public.apiBase

  let refreshing: Promise<boolean> | null = null

  async function tryRefresh(): Promise<boolean> {
    if (refreshing) return refreshing
    refreshing = (async () => {
      try {
        await $fetch(`${apiBase}/api/auth/refresh`, {
          method: 'POST',
          credentials: 'include',
        })
        return true
      } catch {
        return false
      } finally {
        refreshing = null
      }
    })()
    return refreshing
  }

  const api: ApiFetch = $fetch.create({
    baseURL: apiBase,
    credentials: 'include',

    onRequest({ options }) {
      // SSR 에서는 브라우저가 보낸 쿠키를 백엔드로 forward 해야 인증이 성립.
      if (import.meta.server) {
        const reqHeaders = useRequestHeaders(['cookie'])
        if (reqHeaders.cookie) {
          const headers = new Headers(options.headers)
          headers.set('cookie', reqHeaders.cookie)
          options.headers = headers
        }
      }
    },

    async onResponseError({ request, response }) {
      if (response.status !== 401) return
      const url = typeof request === 'string' ? request : request.url
      if (url.includes('/api/auth/login') || url.includes('/api/auth/refresh')) return

      const refreshed = await tryRefresh()
      if (!refreshed && import.meta.client) {
        await navigateTo('/auth/login')
      }
    },

    retry: 1,
    retryStatusCodes: [401],
  })

  return {
    provide: {
      api,
    },
  }
})

declare module '#app' {
  interface NuxtApp {
    $api: ApiFetch
  }
}
declare module 'vue' {
  interface ComponentCustomProperties {
    $api: ApiFetch
  }
}
