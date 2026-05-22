/** 인증된 $api 인스턴스 헬퍼. */
export function useApi() {
  return useNuxtApp().$api
}
