/**
 * vuedraggable(SortableJS) 공통 옵션 — 순서 변경 모달들이 같이 쓴다.
 * - forceFallback: 네이티브 HTML5 DnD 대신 마우스/터치 이벤트로 끈다.
 *   데스크톱 앱(Tauri 웹뷰)은 드래그 이벤트를 파일 드롭 처리기가 가로채 HTML5 DnD 가 시작조차 안 되고,
 *   브라우저마다 드래그 이미지가 제각각이라 모든 환경에서 같은 폴백을 쓴다.
 * - fallbackOnBody: 끌리는 복제본을 body 에 붙여 모달의 overflow 에 잘리지 않게 한다.
 * - fallbackTolerance: 3px 이상 움직여야 드래그 시작 (핸들 클릭과 구분).
 * - fallbackClass 스타일은 assets/css/tailwind.css 의 .qam-drag-fallback.
 */
export const sortableOptions = {
  handle: '.drag-handle',
  ghostClass: 'opacity-50',
  forceFallback: true,
  fallbackOnBody: true,
  fallbackTolerance: 3,
  fallbackClass: 'qam-drag-fallback',
  animation: 150,
} as const
