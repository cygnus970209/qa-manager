<script setup lang="ts">
import { BellRing, AlertTriangle } from '@lucide/vue'
import AppDialog from '~/components/base/AppDialog.vue'

defineProps<{ open: boolean }>()
const emit = defineEmits<{ close: []; confirm: [] }>()
</script>

<template>
  <AppDialog :open="open" title="Teams 알림 설정 안내" @close="emit('close')">
    <div class="space-y-4">
      <div class="flex items-start gap-2.5 rounded-lg bg-rose-50 px-3.5 py-3">
        <AlertTriangle class="mt-0.5 h-5 w-5 shrink-0 text-rose-500" />
        <p class="text-sm text-rose-700">
          <span class="font-semibold">아직 Teams 봇을 설치하지 않으셨어요.</span><br />
          설치하지 않으면 QA 배정·코멘트·답글 등의
          <span class="font-semibold">알림을 받을 수 없습니다.</span>
        </p>
      </div>

      <p class="text-sm leading-relaxed text-slate-600">
        Teams에서 <span class="font-medium text-slate-800">QA Manager 봇</span>을 설치하면
        담당 업무에 대한 알림을 1:1 메시지로 받아볼 수 있습니다. 아래 화면을 참고하세요.
      </p>

      <img
        src="/teams_app.jpg"
        alt="Teams 봇 설치 안내"
        class="w-full rounded-lg border border-slate-200"
      />
    </div>

    <template #footer>
      <button
        type="button"
        class="rounded-md px-3 py-1.5 text-xs font-medium text-slate-500 hover:bg-slate-100"
        @click="emit('close')"
      >
        나중에
      </button>
      <button
        type="button"
        class="inline-flex items-center gap-1.5 rounded-md bg-emerald-600 px-4 py-1.5 text-xs font-semibold text-white hover:bg-emerald-700"
        @click="emit('confirm')"
      >
        <BellRing class="h-4 w-4" />
        설정하러 가기
      </button>
    </template>
  </AppDialog>
</template>
