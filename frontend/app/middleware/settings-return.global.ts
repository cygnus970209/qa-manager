/**
 * 설정 화면(/settings/*)은 앱 위에 전체 화면으로 열리므로, 들어오기 직전 화면을 기억해 두었다가
 * 닫기(ESC)에서 그 화면으로 돌아간다. 설정 안에서 항목 간 이동은 갱신하지 않는다.
 */
export default defineNuxtRouteMiddleware((to, from) => {
  if (!to.path.startsWith('/settings')) return
  if (from.path.startsWith('/settings')) return
  // 최초 진입(새로고침·직접 URL)은 from === to 라 이전 화면이 없다 → 기본값(대시보드) 유지
  if (from.fullPath === to.fullPath) return
  useState<string>('settings-return-to', () => '/').value = from.fullPath
})
