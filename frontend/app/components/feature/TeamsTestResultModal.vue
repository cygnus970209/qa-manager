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
      <div class="w-full max-w-md rounded-xl bg-white shadow-xl dark:bg-slate-900">
        <div class="flex items-center justify-between border-b border-slate-100 px-5 py-4 dark:border-slate-800">
          <h3 class="text-base font-semibold text-slate-800 dark:text-slate-100">
            {{ $t('admin.teams.testTitle') }}
            <span v-if="memberName" class="ml-1 text-sm font-normal text-slate-500 dark:text-slate-400">— {{ memberName }}</span>
          </h3>
          <button
            type="button"
            class="rounded-lg p-1 text-slate-400 hover:bg-slate-100 hover:text-slate-600 dark:text-slate-500 dark:hover:bg-slate-800 dark:hover:text-slate-300"
            @click="$emit('close')"
          >
            <X class="h-4 w-4" />
          </button>
        </div>

        <div class="px-5 py-4">
          <div v-if="loading" class="flex items-center justify-center gap-2 py-8 text-sm text-slate-500 dark:text-slate-400">
            <Loader2 class="h-5 w-5 animate-spin" />
            {{ $t('admin.teams.sending') }}
          </div>

          <template v-else-if="result">
            <!-- 결과 배너 -->
            <div
              :class="[
                'mb-4 rounded-lg px-3 py-2 text-sm',
                result.success
                  ? 'bg-emerald-50 text-emerald-700 dark:bg-emerald-500/10 dark:text-emerald-300'
                  : 'bg-rose-50 text-rose-700 dark:bg-rose-500/10 dark:text-rose-300',
              ]"
            >
              <p v-if="result.success" class="font-medium">{{ $t('admin.teams.sentSuccess') }}</p>
              <p v-else class="font-medium">{{ $t('admin.teams.sendFailed') }}</p>
              <p v-if="result.errorMessage" class="mt-1 whitespace-pre-wrap text-xs text-rose-600 dark:text-rose-400">
                {{ result.errorMessage }}
              </p>
            </div>

            <!-- 단계별 체크리스트 -->
            <ul class="space-y-2 text-sm">
              <li class="flex items-center gap-2">
                <span :class="['flex h-5 w-5 items-center justify-center rounded-full', result.configOk ? 'bg-emerald-100 text-emerald-600 dark:bg-emerald-500/20 dark:text-emerald-400' : 'bg-slate-100 text-slate-400 dark:bg-slate-800 dark:text-slate-500']">
                  <Check v-if="result.configOk" class="h-3 w-3" />
                  <X v-else class="h-3 w-3" />
                </span>
                <span class="text-slate-700 dark:text-slate-200">{{ $t('admin.teams.checkConfig') }}</span>
              </li>
              <li class="flex items-center gap-2">
                <span :class="['flex h-5 w-5 items-center justify-center rounded-full', result.email ? 'bg-emerald-100 text-emerald-600 dark:bg-emerald-500/20 dark:text-emerald-400' : 'bg-slate-100 text-slate-400 dark:bg-slate-800 dark:text-slate-500']">
                  <Check v-if="result.email" class="h-3 w-3" />
                  <X v-else class="h-3 w-3" />
                </span>
                <span class="text-slate-700 dark:text-slate-200">
                  {{ $t('admin.teams.checkEmail') }}
                  <span v-if="result.email" class="ml-1 text-xs text-slate-500 dark:text-slate-400">({{ result.email }})</span>
                </span>
              </li>
              <li class="flex items-center gap-2">
                <span :class="['flex h-5 w-5 items-center justify-center rounded-full', result.notifyEnabled ? 'bg-emerald-100 text-emerald-600 dark:bg-emerald-500/20 dark:text-emerald-400' : 'bg-slate-100 text-slate-400 dark:bg-slate-800 dark:text-slate-500']">
                  <Check v-if="result.notifyEnabled" class="h-3 w-3" />
                  <X v-else class="h-3 w-3" />
                </span>
                <span class="text-slate-700 dark:text-slate-200">{{ $t('admin.teams.checkNotify') }}</span>
              </li>
              <li class="flex items-center gap-2">
                <span :class="['flex h-5 w-5 items-center justify-center rounded-full', result.aadMapped ? 'bg-emerald-100 text-emerald-600 dark:bg-emerald-500/20 dark:text-emerald-400' : 'bg-slate-100 text-slate-400 dark:bg-slate-800 dark:text-slate-500']">
                  <Check v-if="result.aadMapped" class="h-3 w-3" />
                  <X v-else class="h-3 w-3" />
                </span>
                <span class="text-slate-700 dark:text-slate-200">{{ $t('admin.teams.checkAad') }}</span>
              </li>
              <li class="flex items-center gap-2">
                <span :class="['flex h-5 w-5 items-center justify-center rounded-full', result.chatOk ? 'bg-emerald-100 text-emerald-600 dark:bg-emerald-500/20 dark:text-emerald-400' : 'bg-slate-100 text-slate-400 dark:bg-slate-800 dark:text-slate-500']">
                  <Check v-if="result.chatOk" class="h-3 w-3" />
                  <X v-else class="h-3 w-3" />
                </span>
                <span class="text-slate-700 dark:text-slate-200">{{ $t('admin.teams.checkChat') }}</span>
              </li>
              <li class="flex items-center gap-2">
                <span :class="['flex h-5 w-5 items-center justify-center rounded-full', result.sent ? 'bg-emerald-100 text-emerald-600 dark:bg-emerald-500/20 dark:text-emerald-400' : 'bg-slate-100 text-slate-400 dark:bg-slate-800 dark:text-slate-500']">
                  <Check v-if="result.sent" class="h-3 w-3" />
                  <X v-else class="h-3 w-3" />
                </span>
                <span class="text-slate-700 dark:text-slate-200">{{ $t('admin.teams.checkSent') }}</span>
              </li>
            </ul>
          </template>
        </div>

        <div class="flex justify-end border-t border-slate-100 px-5 py-3 dark:border-slate-800">
          <button
            type="button"
            class="rounded-lg bg-slate-800 px-4 py-2 text-sm font-medium text-white hover:bg-slate-700 dark:bg-slate-700 dark:hover:bg-slate-600"
            @click="$emit('close')"
          >
            {{ $t('common.actions.close') }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>
