import { reactive } from 'vue'

/**
 * 앱 내 confirm/alert (Promise 기반).
 *
 * window.confirm / window.alert 는 Tauri(WKWebView) 등 임베디드 웹뷰에서 구현되지 않아
 * 항상 false/무시로 처리된다 → 데스크톱 앱에서 닫기 가드가 먹통이 되는 원인.
 * 반드시 이 컴포저블을 사용할 것. 표시는 app.vue 의 <AppDialogHost /> 싱글톤이 담당한다.
 *
 *   const { confirmDialog } = useAppDialog()
 *   if (!(await confirmDialog({ message: t('...') }))) return
 */

export interface AppDialogOptions {
  /** 본문 (필수) */
  message: string
  /** 강조 제목 (선택) */
  title?: string
  /** 확인 버튼 라벨 (기본: common.actions.confirm) */
  confirmLabel?: string
  /** 취소 버튼 라벨 (기본: common.actions.cancel, confirm 모드 전용) */
  cancelLabel?: string
  /** true 면 확인 버튼을 위험(빨강) 스타일로 */
  danger?: boolean
}

interface AppDialogState extends Required<Pick<AppDialogOptions, 'message'>> {
  open: boolean
  mode: 'confirm' | 'alert'
  title: string
  confirmLabel: string
  cancelLabel: string
  danger: boolean
}

// 모듈 스코프 싱글톤 — 다이얼로그는 클라이언트 이벤트 핸들러에서만 열리므로 SSR 공유 문제 없음
const state = reactive<AppDialogState>({
  open: false,
  mode: 'confirm',
  title: '',
  message: '',
  confirmLabel: '',
  cancelLabel: '',
  danger: false,
})

let resolver: ((value: boolean) => void) | null = null

function show(mode: 'confirm' | 'alert', opts: AppDialogOptions): Promise<boolean> {
  if (!import.meta.client) return Promise.resolve(false)
  // 이미 열린 다이얼로그가 있으면 취소 처리 후 교체
  resolver?.(false)
  state.mode = mode
  state.title = opts.title ?? ''
  state.message = opts.message
  state.confirmLabel = opts.confirmLabel ?? ''
  state.cancelLabel = opts.cancelLabel ?? ''
  state.danger = opts.danger ?? false
  state.open = true
  return new Promise((resolve) => {
    resolver = resolve
  })
}

export function useAppDialog() {
  /** 확인/취소 — 확인 시 true */
  function confirmDialog(opts: AppDialogOptions): Promise<boolean> {
    return show('confirm', opts)
  }

  /** 알림 — 확인 버튼만 표시 */
  async function alertDialog(opts: AppDialogOptions): Promise<void> {
    await show('alert', opts)
  }

  /** AppDialogHost 전용 — 버튼/ESC 처리 */
  function resolveDialog(result: boolean) {
    if (!state.open) return
    state.open = false
    resolver?.(result)
    resolver = null
  }

  return { dialogState: state, confirmDialog, alertDialog, resolveDialog }
}
