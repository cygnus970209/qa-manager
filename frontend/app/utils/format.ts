/** ISO datetime → 'YYYY-MM-DD' */
export function formatDate(iso: string | null | undefined): string {
  if (!iso) return ''
  return iso.slice(0, 10)
}

/** ISO datetime → 'YYYY-MM-DD HH:mm' */
export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return ''
  return iso.replace('T', ' ').slice(0, 16)
}

/** 상대 시간(간단한 한국어). 1시간 미만은 분, 24시간 미만은 시간, 그 외는 날짜. */
export function timeAgo(iso: string | null | undefined): string {
  if (!iso) return ''
  const t = new Date(iso).getTime()
  if (Number.isNaN(t)) return iso
  const diff = Date.now() - t
  const sec = Math.floor(diff / 1000)
  if (sec < 60) return '방금 전'
  const min = Math.floor(sec / 60)
  if (min < 60) return `${min}분 전`
  const hr = Math.floor(min / 60)
  if (hr < 24) return `${hr}시간 전`
  return formatDate(iso)
}
