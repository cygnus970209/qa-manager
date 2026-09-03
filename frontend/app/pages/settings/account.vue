<script setup lang="ts">
import SettingsHeader from '~/components/base/SettingsHeader.vue'
import AccountForm from '~/components/feature/settings/AccountForm.vue'

definePageMeta({ layout: 'settings' })
</script>

<template>
  <div>
    <SettingsHeader :title="$t('shell.settings.account.title')" :subtitle="$t('shell.settings.account.subtitle')" />
    <AccountForm />
  </div>
</template>
