<script setup lang="ts">
import SettingsHeader from '~/components/base/SettingsHeader.vue'
import MembersManager from '~/components/feature/settings/MembersManager.vue'

definePageMeta({ layout: 'settings' })

// 관리자 전용 — 일반 멤버는 내 계정으로 돌려보낸다 (API 는 백엔드 403 으로 별도 보호됨)
const auth = useAuthStore()
const router = useRouter()
watchEffect(() => {
  if (auth.user && auth.user.accountRole !== 'ADMIN') router.replace('/settings/account')
})
</script>

<template>
  <div>
    <SettingsHeader :title="$t('shell.settings.members.title')" :subtitle="$t('shell.settings.members.subtitle')" />
    <MembersManager />
  </div>
</template>
