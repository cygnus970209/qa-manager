<script setup lang="ts">
/**
 * /settings 진입점 — 첫 항목(내 계정)으로 보낸다.
 * 예전 링크(?sub=ms-teams, ?sub=github) 도 새 항목으로 이어준다.
 */
definePageMeta({ layout: 'settings' })

const route = useRoute()
const sub = route.query.sub
const target = sub === 'ms-teams' ? '/settings/notifications#teams'
  : sub === 'github' ? '/settings/github'
  : sub === 'notifications' ? '/settings/notifications'
  : '/settings/account'
await navigateTo(target, { replace: true })
</script>

<template>
  <div />
</template>
