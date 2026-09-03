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
    '@nuxtjs/i18n',
    '@nuxtjs/color-mode',
  ],

  // 커스텀 base CSS (color-scheme + 다크 대비 완화 오버라이드).
  // 명시하지 않으면 @nuxtjs/tailwindcss 가 내장 기본 CSS 를 사용해 이 파일이 무시된다.
  tailwindcss: {
    cssPath: '~/assets/css/tailwind.css',
  },

  // 다크모드. Tailwind `dark:` variant(class 전략)와 연동 — html 에 'dark'/'light' 클래스 부착.
  // 기본은 OS 설정(system) 따라가고, 사용자가 선택하면 localStorage 에 기억된다(FOUC 방지 스크립트 내장).
  colorMode: {
    classSuffix: '',
    preference: 'system',
    fallback: 'light',
    storageKey: 'qam-color-mode',
  },

  // 다국어(ko/en). URL prefix 없이 쿠키로 언어를 기억한다(내부 도구 + noindex 라 SEO용 prefix 불필요).
  i18n: {
    strategy: 'no_prefix',
    defaultLocale: 'ko',
    locales: [
      {
        code: 'ko',
        language: 'ko-KR',
        name: '한국어',
        files: ['ko/common.json', 'ko/shell.json', 'ko/auth.json', 'ko/dashboard.json', 'ko/project.json', 'ko/qa.json', 'ko/admin.json', 'ko/demo.json', 'ko/testcase.json', 'ko/testflow.json', 'ko/testrun.json'],
      },
      {
        code: 'en',
        language: 'en-US',
        name: 'English',
        files: ['en/common.json', 'en/shell.json', 'en/auth.json', 'en/dashboard.json', 'en/project.json', 'en/qa.json', 'en/admin.json', 'en/demo.json', 'en/testcase.json', 'en/testflow.json', 'en/testrun.json'],
      },
    ],
    detectBrowserLanguage: {
      useCookie: true,
      cookieKey: 'qam_locale',
      fallbackLocale: 'ko',
    },
  },

  devServer: {
    port: 3000,
  },

  // 새 빌드 감지 주기(기본 1시간 → 10분). 발견하면 app:manifest:update → 배너 + 다음 화면 이동 때 재로드.
  // 배포 직후에는 SSE 재연결 시점에 즉시 확인한다 (composables/useAppUpdate.ts).
  experimental: {
    checkOutdatedBuildInterval: 10 * 60 * 1000,
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
      // 첨부 파일 최대 크기 (MB). NUXT_PUBLIC_UPLOAD_MAX_FILE_SIZE_MB 로 오버라이드.
      // 백엔드 presign 검증(UPLOAD_MAX_FILE_SIZE_MB)과 값을 맞출 것.
      uploadMaxFileSizeMb: 100,
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
