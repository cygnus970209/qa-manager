// https://nuxt.com/docs/api/configuration/nuxt-config
export default defineNuxtConfig({
  compatibilityDate: '2025-07-15',
  devtools: { enabled: true },

  ssr: true,

  modules: [
    '@nuxtjs/tailwindcss',
    '@pinia/nuxt',
    '@vueuse/nuxt',
  ],

  devServer: {
    port: 3000,
  },

  app: {
    head: {
      title: 'QA Manager',
      htmlAttrs: { lang: 'ko' },
      meta: [
        { charset: 'utf-8' },
        { name: 'viewport', content: 'width=device-width, initial-scale=1' },
      ],
    },
  },

  // NUXT_PUBLIC_API_BASE 환경변수가 있으면 자동 오버라이드 (Nuxt 규칙).
  runtimeConfig: {
    public: {
      apiBase: 'http://localhost:8080',
    },
  },

  // 모든 응답에 기본 보안 헤더 부착.
  // CSP: Nuxt SSR 은 hydration 용 인라인 <script> 를 주입하므로 'unsafe-inline' 필요.
  //      더 강한 보안(nonce 기반)을 원하면 nuxt-security 모듈 도입 고려.
  //      운영에서 외부 도메인이 추가되면 connect-src/img-src 갱신 필요.
  routeRules: {
    '/**': {
      headers: {
        'X-Frame-Options': 'DENY',
        'X-Content-Type-Options': 'nosniff',
        'Referrer-Policy': 'strict-origin-when-cross-origin',
        'Permissions-Policy': 'camera=(), microphone=(), geolocation=()',
        'Content-Security-Policy': [
          "default-src 'self'",
          "img-src 'self' data: https:",
          "style-src 'self' 'unsafe-inline'",
          "script-src 'self' 'unsafe-inline'",
          "font-src 'self' data:",
          "connect-src 'self' http://localhost:8080 https:",
          "frame-ancestors 'none'",
          "base-uri 'self'",
          "form-action 'self'",
        ].join('; '),
      },
    },
  },

  typescript: {
    strict: true,
  },
})
