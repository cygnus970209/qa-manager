<script setup lang="ts">
import { Check, X, Loader2 } from '@lucide/vue'
import type { TeamsTestResult } from '~/types/api'

defineProps<{
  open: boolean
  loading: boolean
  result: TeamsTestResult | null
  memberName?: string
}>()

defineEmits<{
  close: []
}>()
</script>

<template>
  <Teleport to="body">
    <div
      v-if="open"
      class="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 px-4"
      @click.self="$emit('close')"
    >
      <div class="w-full max-w-md rounded-xl bg-white shadow-xl">
        <div class="flex items-center justify-between border-b border-slate-100 px-5 py-4">
          <h3 class="text-base font-semibold text-slate-800">
            Teams 발송 테스트
            <span v-if="memberName" class="ml-1 text-sm font-normal text-slate-500">— {{ memberName }}</span>
          </h3>
          <button
            type="button"
            class="rounded-lg p-1 text-slate-400 hover:bg-slate-100 hover:text-slate-600"
            @click="$emit('close')"
          >
            <X class="h-4 w-4" />
          </button>
        </div>

        <div class="px-5 py-4">
          <div v-if="loading" class="flex items-center justify-center gap-2 py-8 text-sm text-slate-500">
            <Loader2 class="h-5 w-5 animate-spin" />
            발송 중...
          </div>

          <template v-else-if="result">
            <!-- 결과 배너 -->
            <div
              :class="[
                'mb-4 rounded-lg px-3 py-2 text-sm',
                result.success
                  ? 'bg-emerald-50 text-emerald-700'
                  : 'bg-rose-50 text-rose-700',
              ]"
            >
              <p v-if="result.success" class="font-medium">테스트 메세지를 발송했습니다. Teams 를 확인해보세요.</p>
              <p v-else class="font-medium">발송 실패</p>
              <p v-if="result.errorMessage" class="mt-1 whitespace-pre-wrap text-xs text-rose-600">
                {{ result.errorMessage }}
              </p>
            </div>

            <!-- 단계별 체크리스트 -->
            <ul class="space-y-2 text-sm">
              <li class="flex items-center gap-2">
                <span :class="['flex h-5 w-5 items-center justify-center rounded-full', result.configOk ? 'bg-emerald-100 text-emerald-600' : 'bg-slate-100 text-slate-400']">
                  <Check v-if="result.configOk" class="h-3 w-3" />
                  <X v-else class="h-3 w-3" />
                </span>
                <span class="text-slate-700">Teams 설정 활성화</span>
              </li>
              <li class="flex items-center gap-2">
                <span :class="['flex h-5 w-5 items-center justify-center rounded-full', result.email ? 'bg-emerald-100 text-emerald-600' : 'bg-slate-100 text-slate-400']">
                  <Check v-if="result.email" class="h-3 w-3" />
                  <X v-else class="h-3 w-3" />
                </span>
                <span class="text-slate-700">
                  Email 등록
                  <span v-if="result.email" class="ml-1 text-xs text-slate-500">({{ result.email }})</span>
                </span>
              </li>
              <li class="flex items-center gap-2">
                <span :class="['flex h-5 w-5 items-center justify-center rounded-full', result.notifyEnabled ? 'bg-emerald-100 text-emerald-600' : 'bg-slate-100 text-slate-400']">
                  <Check v-if="result.notifyEnabled" class="h-3 w-3" />
                  <X v-else class="h-3 w-3" />
                </span>
                <span class="text-slate-700">알림 토글 ON</span>
              </li>
              <li class="flex items-center gap-2">
                <span :class="['flex h-5 w-5 items-center justify-center rounded-full', result.aadMapped ? 'bg-emerald-100 text-emerald-600' : 'bg-slate-100 text-slate-400']">
                  <Check v-if="result.aadMapped" class="h-3 w-3" />
                  <X v-else class="h-3 w-3" />
                </span>
                <span class="text-slate-700">Azure AD 사용자 매핑</span>
              </li>
              <li class="flex items-center gap-2">
                <span :class="['flex h-5 w-5 items-center justify-center rounded-full', result.chatOk ? 'bg-emerald-100 text-emerald-600' : 'bg-slate-100 text-slate-400']">
                  <Check v-if="result.chatOk" class="h-3 w-3" />
                  <X v-else class="h-3 w-3" />
                </span>
                <span class="text-slate-700">1:1 chat 준비</span>
              </li>
              <li class="flex items-center gap-2">
                <span :class="['flex h-5 w-5 items-center justify-center rounded-full', result.sent ? 'bg-emerald-100 text-emerald-600' : 'bg-slate-100 text-slate-400']">
                  <Check v-if="result.sent" class="h-3 w-3" />
                  <X v-else class="h-3 w-3" />
                </span>
                <span class="text-slate-700">메세지 발송</span>
              </li>
            </ul>
          </template>
        </div>

        <div class="flex justify-end border-t border-slate-100 px-5 py-3">
          <button
            type="button"
            class="rounded-lg bg-slate-800 px-4 py-2 text-sm font-medium text-white hover:bg-slate-700"
            @click="$emit('close')"
          >
            닫기
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>
