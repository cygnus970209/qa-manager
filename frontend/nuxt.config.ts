// https://nuxt.com/docs/api/configuration/nuxt-config

// nuxt.config 는 Node 컨텍스트에서 실행된다. @types/node 미설치 환경을 위한 최소 타입 선언.
declare const process: { env: Record<string, string | undefined> }

// DEMO_BUILD=true 로 빌드하면 데모 모드(정적 SPA + localStorage mock)로 동작한다.
//   예: cd frontend && DEMO_BUILD=true npm run generate
const isDemoBuild = process.env.DEMO_BUILD === 'true'

export default defineNuxtConfig({
  compatibilityDate: '2025-07-15',
  devtools: { enabled: true },

  // 데모는 백엔드 없이 클라이언트(localStorage)에서만 동작하므로 SSR 을 끈다.
  ssr: !isDemoBuild,

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
      // 데모 모드 여부. DEMO_BUILD=true 빌드에서 켜진다(전역 '데모 버전' 배너 + localStorage mock).
      demoMode: isDemoBuild,
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
          // GitHub App Manifest flow 는 github.com 으로 hidden form POST 가 필요하다.
          "form-action 'self' https://github.com",
        ].join('; '),
      },
    },
  },

  typescript: {
    strict: true,
  },
})
