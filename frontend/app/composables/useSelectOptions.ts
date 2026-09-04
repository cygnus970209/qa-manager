import type { AccountRole, ProjectStatus, QaPriority, QaStatus, UpdateStatus } from '~/types/api'

/** AppSelect 의 기본 옵션 모양 */
export type SelectOption<V = string | number | null> = { value: V; label: string }

export const QA_STATUS_KEYS: QaStatus[] = ['needs_fix', 'in_progress', 'fix_done', 'confirmed', 'on_hold', 'needs_recheck']
export const QA_PRIORITY_KEYS: QaPriority[] = ['low', 'medium', 'high', 'critical']
export const PROJECT_STATUS_KEYS: ProjectStatus[] = ['active', 'paused', 'completed']
export const UPDATE_STATUS_KEYS: UpdateStatus[] = ['in_progress', 'testing', 'released']

/** 소문자 코드 옵션을 API enum(대문자) 값으로 바꾼다 — 생성/수정 폼용 */
export function upperOptions<V extends string>(opts: SelectOption<V>[]): SelectOption<Uppercase<V>>[] {
  return opts.map((o) => ({ value: o.value.toUpperCase() as Uppercase<V>, label: o.label }))
}

/**
 * 상태·우선순위처럼 앱 곳곳에서 같은 목록을 고르는 AppSelect 옵션.
 * 라벨은 i18n 이라 computed 로 두어 언어 전환에 따라간다. 값은 소문자 코드(types/api 의 union)이고
 * API enum(대문자)이 필요한 폼은 upperOptions() 로 바꿔 쓴다.
 */
export function useSelectOptions() {
  const { t } = useI18n()
  const qaStatus = computed<SelectOption<QaStatus>[]>(() => QA_STATUS_KEYS.map((k) => ({ value: k, label: t(`common.qaStatus.${k}`) })))
  /** 낮음 → 긴급 순 */
  const priority = computed<SelectOption<QaPriority>[]>(() => QA_PRIORITY_KEYS.map((k) => ({ value: k, label: t(`common.priority.${k}`) })))
  const projectStatus = computed<SelectOption<ProjectStatus>[]>(() => PROJECT_STATUS_KEYS.map((k) => ({ value: k, label: t(`common.projectStatus.${k}`) })))
  const updateStatus = computed<SelectOption<UpdateStatus>[]>(() => UPDATE_STATUS_KEYS.map((k) => ({ value: k, label: t(`common.updateStatus.${k}`) })))
  const accountRole = computed<SelectOption<AccountRole>[]>(() => [
    { value: 'ADMIN', label: t('common.accountRole.admin') },
    { value: 'MEMBER', label: t('common.accountRole.member') },
  ])
  return { qaStatus, priority, projectStatus, updateStatus, accountRole }
}
