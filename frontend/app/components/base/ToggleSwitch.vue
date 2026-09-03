<script setup lang="ts">
/** on/off 스위치. v-model 로 boolean 을 바인딩한다 */
const model = defineModel<boolean>({ default: false })
defineProps<{ disabled?: boolean }>()
</script>

<template>
  <label :class="['relative inline-flex shrink-0 items-center', disabled ? 'cursor-not-allowed opacity-60' : 'cursor-pointer']">
    <input v-model="model" type="checkbox" class="peer sr-only" :disabled="disabled" />
    <div
      class="peer h-6 w-11 rounded-full bg-slate-200 transition-colors after:absolute after:left-[2px] after:top-[2px] after:h-5 after:w-5 after:rounded-full after:border after:border-slate-300 after:bg-white after:transition-all after:content-[''] peer-checked:bg-emerald-500 peer-checked:after:translate-x-full peer-checked:after:border-white peer-focus-visible:ring-2 peer-focus-visible:ring-emerald-500/30 dark:bg-slate-700 dark:after:border-slate-600 dark:peer-checked:bg-emerald-600"
    />
  </label>
</template>
