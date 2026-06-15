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
        // 검색엔진/봇 인덱싱 차단 (내부 도구라 공개 노출 불필요)
        { name: 'robots', content: 'noindex, nofollow, noarchive, nosnippet, noimageindex' },
        { name: 'googlebot', content: 'noindex, nofollow, noarchive, nosnippet, noimageindex' },
      ],
    },
  },

  // NUXT_PUBLIC_API_BASE 환경변수가 있으면 자동 오버라이드 (Nuxt 규칙).
  runtimeConfig: {
    public: {
      apiBase: 'http://localhost:8080',
      // 데모 모드: NUXT_PUBLIC_DEMO_MODE=true 면 전역 '데모 버전' 배너 + 로그인 계정 안내 노출.
      demoMode: false,
      // 데모 계정 목록. "라벨|아이디|비밀번호" 를 세미콜론(;)으로 구분 (NUXT_PUBLIC_DEMO_ACCOUNTS).
      demoAccounts: '',
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
        'Referrer-Policy': 'no-referrer',
        // robots meta 보다 강력 — 응답 헤더 단계에서 검색엔진 차단 (HTML 외 자원에도 적용)
        'X-Robots-Tag': 'noindex, nofollow, noarchive, nosnippet, noimageindex',
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
