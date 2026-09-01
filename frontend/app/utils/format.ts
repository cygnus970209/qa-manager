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

/** 컴포넌트 렌더 컨텍스트 밖(테스트 등)에서 호출돼도 안전하게 현재 i18n 을 얻는다. */
function activeI18n(): { locale: string; t: (key: string) => string } | null {
  try {
    const { $i18n } = useNuxtApp() as any
    if ($i18n?.locale?.value) return { locale: $i18n.locale.value, t: $i18n.t.bind($i18n) }
  } catch { /* nuxt 컨텍스트 밖 */ }
  return null
}

/** 상대 시간(현재 언어 기준). 1시간 미만은 분, 24시간 미만은 시간, 그 외는 날짜. */
export function timeAgo(iso: string | null | undefined): string {
  if (!iso) return ''
  const t = new Date(iso).getTime()
  if (Number.isNaN(t)) return iso
  const i18n = activeI18n()
  const locale = i18n?.locale ?? 'ko'
  const diff = Date.now() - t
  const sec = Math.floor(diff / 1000)
  if (sec < 60) return i18n ? i18n.t('common.time.justNow') : '방금 전'
  const rtf = new Intl.RelativeTimeFormat(locale, { numeric: 'always' })
  const min = Math.floor(sec / 60)
  if (min < 60) return rtf.format(-min, 'minute')
  const hr = Math.floor(min / 60)
  if (hr < 24) return rtf.format(-hr, 'hour')
  return formatDate(iso)
}
