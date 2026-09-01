/** 첨부 URL(S3 공개 URL 또는 데모 모드 data URL)이 PDF 인지 판별한다. */
export function isPdfUrl(url: string): boolean {
  if (url.startsWith('data:')) return url.startsWith('data:application/pdf')
  const path = url.split(/[?#]/)[0] ?? url
  return path.toLowerCase().endsWith('.pdf')
}

/** 컴포넌트 렌더 컨텍스트 밖에서 호출돼도 안전하게 번역을 얻는다 (format.ts 의 activeI18n 과 동일 패턴). */
function pdfDocumentLabel(): string {
  try {
    const { $i18n } = useNuxtApp() as any
    return $i18n.t('qa.attachments.pdfDocument')
  } catch {
    return 'PDF 문서'
  }
}

/** S3 key 마지막 세그먼트 "{uuid}-{원본파일명}" 에서 원본 파일명을 복원한다. */
export function attachmentFileName(url: string): string {
  if (url.startsWith('data:')) return pdfDocumentLabel()
  try {
    const path = url.split(/[?#]/)[0] ?? url
    const last = decodeURIComponent(path.split('/').pop() ?? '')
    const name = last.replace(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}-/i, '')
    return name || pdfDocumentLabel()
  } catch {
    return pdfDocumentLabel()
  }
}

/**
 * PDF 를 새 탭(브라우저 내장 뷰어)으로 연다.
 * 데모 모드의 data URL 은 크롬이 최상위 내비게이션을 차단하므로 blob URL 로 변환해서 연다.
 */
export async function openPdfInNewTab(url: string): Promise<void> {
  if (url.startsWith('data:')) {
    const blob = await (await fetch(url)).blob()
    const blobUrl = URL.createObjectURL(blob)
    window.open(blobUrl, '_blank', 'noopener')
    setTimeout(() => URL.revokeObjectURL(blobUrl), 60_000)
    return
  }
  window.open(url, '_blank', 'noopener')
}
