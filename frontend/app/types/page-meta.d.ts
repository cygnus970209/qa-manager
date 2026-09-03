/**
 * definePageMeta 확장 필드.
 * - fullBleed: default 레이아웃의 본문 여백/최대폭 없이 화면을 꽉 채운다 (알림 분할 화면 등).
 */
declare module '#app' {
  interface PageMeta {
    fullBleed?: boolean
  }
}

declare module 'vue-router' {
  interface RouteMeta {
    fullBleed?: boolean
  }
}

export {}
