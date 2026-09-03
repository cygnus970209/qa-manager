import type { NotificationSettings } from '~/types/api'

/**
 * 내 알림 설정(/api/me/notification-settings) 로드·저장.
 * 알림 화면(종류별 토글·방해금지)과 MS Teams 화면(마스터 토글)이 같은 객체를 나눠 쓴다 — 각 화면이 전체를 로드하고 전체를 저장한다.
 */
export function useNotificationSettings() {
  const api = useApi()
  const { t } = useI18n()

  const settings = reactive<NotificationSettings>({
    teamsNotifyEnabled: true,
    notifyQaEnabled: true,
    notifyCommentEnabled: true,
    notifyReplyEnabled: true,
    quietHoursStart: null,
    quietHoursEnd: null,
  })
  const quietEnabled = ref(false)
  const loading = ref(true)
  const saving = ref(false)
  const message = ref('')
  const isError = ref(false)

  function apply(s: NotificationSettings) {
    Object.assign(settings, s)
    quietEnabled.value = !!(s.quietHoursStart && s.quietHoursEnd)
  }

  async function load() {
    loading.value = true
    try {
      apply(await api<NotificationSettings>('/api/me/notification-settings'))
    } catch {
      // 로드 실패 시 기본값 유지
    } finally {
      loading.value = false
    }
  }

  async function save() {
    saving.value = true
    message.value = ''
    isError.value = false
    try {
      const body: NotificationSettings = {
        teamsNotifyEnabled: settings.teamsNotifyEnabled,
        notifyQaEnabled: settings.notifyQaEnabled,
        notifyCommentEnabled: settings.notifyCommentEnabled,
        notifyReplyEnabled: settings.notifyReplyEnabled,
        quietHoursStart: quietEnabled.value ? (settings.quietHoursStart || '22:00') : null,
        quietHoursEnd: quietEnabled.value ? (settings.quietHoursEnd || '08:00') : null,
      }
      apply(await api<NotificationSettings>('/api/me/notification-settings', { method: 'PUT', body }))
      message.value = t('admin.settings.saved')
    } catch {
      isError.value = true
      message.value = t('admin.settings.saveFailed')
    } finally {
      saving.value = false
    }
  }

  return { settings, quietEnabled, loading, saving, message, isError, load, save }
}
