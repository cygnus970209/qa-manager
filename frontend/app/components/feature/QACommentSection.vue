<script setup lang="ts">
import {
  Check,
  CornerDownRight,
  Edit3,
  Image as ImageIcon,
  MessageSquare,
  Plus,
  Reply,
  Trash2,
  X,
} from '@lucide/vue'
import ImageLightbox from '~/components/base/ImageLightbox.vue'
import { timeAgo } from '~/utils/format'
import type { Member, QaComment } from '~/types/api'

const props = defineProps<{
  qaItemId: number
  comments: QaComment[]
  members: Member[]
}>()
const emit = defineEmits<{ refreshed: [comments: QaComment[]] }>()

const qaApi = useQa()
const upload = useUpload()
const auth = useAuthStore()

const EMOJIS = ['👍', '👎', '👀', '🎉', '😄', '❤️', '🤔', '🚀']
const MAX_LEN = 500

/* ─── 트리 ─── */
const tree = computed(() => {
  const top = props.comments.filter((c) => !c.parentId)
  const childrenMap = new Map<number, QaComment[]>()
  for (const c of props.comments) {
    if (c.parentId) {
      const arr = childrenMap.get(c.parentId) ?? []
      arr.push(c)
      childrenMap.set(c.parentId, arr)
    }
  }
  return { top, childrenMap }
})

/* ─── 새 댓글 ─── */
const newContent = ref('')
const newImages = ref<string[]>([])
const newRef = ref<HTMLTextAreaElement | null>(null)
const submitting = ref(false)
const uploading = ref(false)
const error = ref<string | null>(null)

/* ─── 수정 ─── */
const editingId = ref<number | null>(null)
const editContent = ref('')
const editImages = ref<string[]>([])
const editRef = ref<HTMLTextAreaElement | null>(null)

/* ─── 인라인 삭제 확인 ─── */
const deletingId = ref<number | null>(null)

/* ─── 답글 ─── */
const replyToId = ref<number | null>(null)
const replyContent = ref('')
const replyImages = ref<string[]>([])
const replyRef = ref<HTMLTextAreaElement | null>(null)

/* ─── 반응 picker ─── */
const reactionPickerId = ref<number | null>(null)
const pickerRef = ref<HTMLElement | null>(null)

/* ─── 멘션 popup ─── */
const showMention = ref(false)
const mentionQuery = ref('')
const mentionMode = ref<'new' | 'edit' | 'reply'>('new')
const mentionIdx = ref(0)
const filteredMembers = computed(() => {
  const q = mentionQuery.value.toLowerCase()
  return props.members.filter((m) => m.name.toLowerCase().includes(q)).slice(0, 8)
})

/* ─── Lightbox ─── */
const lightboxSrc = ref<string | null>(null)

/* ─── 자동 textarea 높이 ─── */
function autoResize(el: HTMLTextAreaElement | null) {
  if (!el) return
  el.style.height = 'auto'
  el.style.height = `${el.scrollHeight}px`
}
watch(newContent, () => autoResize(newRef.value))
watch(editContent, () => autoResize(editRef.value))
watch(replyContent, () => autoResize(replyRef.value))

/* ─── 바깥 클릭으로 반응 picker 닫기 ─── */
function onDocClick(e: MouseEvent) {
  if (reactionPickerId.value === null) return
  if (pickerRef.value && pickerRef.value.contains(e.target as Node)) return
  reactionPickerId.value = null
}
onMounted(() => document.addEventListener('mousedown', onDocClick))
onBeforeUnmount(() => document.removeEventListener('mousedown', onDocClick))

/* ─── 데이터 액션 ─── */
async function refresh() {
  const next = await qaApi.listComments(props.qaItemId)
  emit('refreshed', next)
}

async function uploadFiles(files: FileList | null, target: 'new' | 'edit' | 'reply') {
  if (!files || files.length === 0) return
  uploading.value = true
  error.value = null
  try {
    const urls: string[] = []
    for (const f of Array.from(files)) {
      urls.push(await upload.uploadImage(f, 'comment_image'))
    }
    if (target === 'new') newImages.value.push(...urls)
    else if (target === 'edit') editImages.value.push(...urls)
    else replyImages.value.push(...urls)
  } catch (e: any) {
    error.value = e?.message ?? '이미지 업로드 실패'
  } finally {
    uploading.value = false
  }
}

async function submitNew() {
  if (!newContent.value.trim() && newImages.value.length === 0) return
  if (newContent.value.length > MAX_LEN) return
  submitting.value = true
  error.value = null
  try {
    await qaApi.createComment(props.qaItemId, {
      content: newContent.value.trim(),
      images: newImages.value.length > 0 ? newImages.value : undefined,
    })
    newContent.value = ''
    newImages.value = []
    await refresh()
  } catch (e: any) {
    error.value = e?.data?.message ?? '댓글 등록 실패'
  } finally {
    submitting.value = false
  }
}

function startEdit(c: QaComment) {
  editingId.value = c.id
  editContent.value = c.content
  editImages.value = [...c.images]
  nextTick(() => editRef.value?.focus())
}
function cancelEdit() {
  editingId.value = null
  editContent.value = ''
  editImages.value = []
}
async function saveEdit() {
  if (editingId.value === null) return
  if (!editContent.value.trim()) return
  if (editContent.value.length > MAX_LEN) return
  await qaApi.updateComment(editingId.value, {
    content: editContent.value.trim(),
    images: editImages.value,
  })
  cancelEdit()
  await refresh()
}

function startReply(id: number) {
  replyToId.value = id
  replyContent.value = ''
  replyImages.value = []
  nextTick(() => replyRef.value?.focus())
}
function cancelReply() {
  replyToId.value = null
  replyContent.value = ''
  replyImages.value = []
}
async function submitReply() {
  if (replyToId.value === null) return
  if (!replyContent.value.trim() && replyImages.value.length === 0) return
  if (replyContent.value.length > MAX_LEN) return
  await qaApi.createComment(props.qaItemId, {
    parentId: replyToId.value,
    content: replyContent.value.trim(),
    images: replyImages.value.length > 0 ? replyImages.value : undefined,
  })
  cancelReply()
  await refresh()
}

async function confirmDelete(id: number) {
  await qaApi.removeComment(id)
  deletingId.value = null
  await refresh()
}

async function onReaction(commentId: number, emoji: string) {
  await qaApi.toggleReaction(commentId, emoji)
  reactionPickerId.value = null
  await refresh()
}

function hasReacted(c: QaComment, emoji: string) {
  if (!auth.user) return false
  return (c.reactions?.[emoji] ?? []).includes(auth.user.id)
}
function reactionCount(c: QaComment, emoji: string) {
  return c.reactions?.[emoji]?.length ?? 0
}
function existingReactionEmojis(c: QaComment) {
  return Object.keys(c.reactions ?? {}).filter((k) => (c.reactions?.[k]?.length ?? 0) > 0)
}

function isMine(c: QaComment) {
  return auth.user?.id === c.author.id
}

/* ─── 멘션 ─── */
function checkMention(el: HTMLTextAreaElement, mode: 'new' | 'edit' | 'reply') {
  const cursor = el.selectionStart ?? 0
  const before = el.value.slice(0, cursor)
  const lastAt = before.lastIndexOf('@')
  if (lastAt === -1) { showMention.value = false; return }
  const after = before.slice(lastAt + 1)
  if (/\s/.test(after)) { showMention.value = false; return }
  mentionQuery.value = after
  mentionMode.value = mode
  showMention.value = true
  mentionIdx.value = 0
}
function insertMention(name: string) {
  const el =
    mentionMode.value === 'edit' ? editRef.value
    : mentionMode.value === 'reply' ? replyRef.value
    : newRef.value
  if (!el) return
  const value = el.value
  const cursor = el.selectionStart ?? 0
  const before = value.slice(0, cursor)
  const lastAt = before.lastIndexOf('@')
  if (lastAt === -1) return
  const beforeAt = value.slice(0, lastAt)
  const afterCursor = value.slice(cursor)
  const next = `${beforeAt}@${name} ${afterCursor}`
  if (mentionMode.value === 'edit') editContent.value = next
  else if (mentionMode.value === 'reply') replyContent.value = next
  else newContent.value = next
  showMention.value = false
  nextTick(() => {
    const pos = beforeAt.length + name.length + 2
    el.setSelectionRange(pos, pos)
    el.focus()
  })
}
function onMentionKey(e: KeyboardEvent) {
  if (!showMention.value) return
  const list = filteredMembers.value
  if (e.key === 'Escape') { showMention.value = false; e.preventDefault() }
  else if (e.key === 'Enter') {
    if (list.length > 0) { insertMention(list[mentionIdx.value].name); e.preventDefault() }
  }
  else if (e.key === 'ArrowDown') {
    mentionIdx.value = Math.min(mentionIdx.value + 1, list.length - 1)
    e.preventDefault()
  }
  else if (e.key === 'ArrowUp') {
    mentionIdx.value = Math.max(mentionIdx.value - 1, 0)
    e.preventDefault()
  }
}

/* ─── 멘션 본문 렌더 분해 ─── */
type Segment = { text: string; mention: boolean }
function renderSegments(text: string): Segment[] {
  const re = /(@[^\s]+)/g
  const parts = text.split(re)
  return parts.map((p) => {
    if (p.startsWith('@')) {
      const name = p.slice(1)
      const isMember = props.members.some((m) => m.name === name)
      return { text: p, mention: isMember }
    }
    return { text: p, mention: false }
  })
}

function memberInitial(name: string) {
  return name.charAt(0)
}
</script>

<template>
  <section class="overflow-hidden rounded-xl border border-slate-200 bg-white">
    <header class="border-b border-slate-100 p-4 md:p-5">
      <h3 class="flex items-center gap-2 text-sm font-semibold text-slate-700">
        <MessageSquare class="h-4 w-4" /> 코멘트
        <span class="ml-1 text-xs font-normal text-slate-400">{{ comments.length }}개</span>
      </h3>
    </header>

    <!-- 목록 -->
    <div class="max-h-[600px] overflow-y-auto">
      <div v-if="tree.top.length === 0" class="py-10 text-center">
        <MessageSquare class="mx-auto mb-2 h-6 w-6 text-slate-300" />
        <p class="text-sm text-slate-400">아직 코멘트가 없습니다</p>
      </div>

      <div v-else class="space-y-5 p-4 md:p-5">
        <div v-for="root in tree.top" :key="root.id">
          <!-- 루트 댓글 -->
          <div class="flex gap-3">
            <div class="shrink-0">
              <img v-if="root.author.avatarUrl" :src="root.author.avatarUrl" :alt="root.author.name" class="h-8 w-8 rounded-full bg-slate-100 object-cover" />
              <div v-else class="flex h-8 w-8 items-center justify-center rounded-full bg-emerald-100 text-sm font-medium text-emerald-600">{{ memberInitial(root.author.name) }}</div>
            </div>
            <div class="min-w-0 flex-1">
              <div class="flex flex-wrap items-center gap-2">
                <span class="text-sm font-medium text-slate-700">{{ root.author.name }}</span>
                <span class="text-xs text-slate-400">{{ timeAgo(root.createdAt) }}</span>
                <span v-if="editingId === root.id" class="text-xs font-medium text-emerald-500">편집 중</span>
              </div>

              <!-- 수정 모드 -->
              <div v-if="editingId === root.id" class="mt-1">
                <div class="relative">
                  <textarea
                    ref="editRef"
                    v-model="editContent"
                    rows="2"
                    :maxlength="MAX_LEN"
                    class="w-full resize-none rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-700 focus:border-emerald-300 focus:outline-none focus:ring-2 focus:ring-emerald-100"
                    @input="checkMention($event.target as HTMLTextAreaElement, 'edit')"
                    @keydown="onMentionKey"
                  />
                  <ul v-if="showMention && mentionMode === 'edit' && filteredMembers.length > 0" class="absolute left-0 right-auto top-full z-20 mt-1 max-h-48 w-52 overflow-y-auto rounded-lg border border-slate-200 bg-white shadow-lg">
                    <li v-for="(m, i) in filteredMembers" :key="m.id">
                      <button type="button" :class="['flex w-full items-center gap-2 px-3 py-2 text-left transition-colors hover:bg-slate-50', i === mentionIdx ? 'bg-emerald-50' : '']" @click="insertMention(m.name)">
                        <img v-if="m.avatarUrl" :src="m.avatarUrl" :alt="m.name" class="h-6 w-6 rounded-full object-cover" />
                        <div v-else class="flex h-6 w-6 items-center justify-center rounded-full bg-emerald-100 text-xs font-medium text-emerald-600">{{ memberInitial(m.name) }}</div>
                        <div class="min-w-0 flex-1">
                          <span class="block text-sm text-slate-700">{{ m.name }}</span>
                          <span class="block text-[10px] text-slate-400">{{ m.role ?? '' }}</span>
                        </div>
                      </button>
                    </li>
                  </ul>
                </div>
                <div v-if="editImages.length > 0" class="mt-2 flex flex-wrap gap-2">
                  <div v-for="(img, idx) in editImages" :key="img + idx" class="group relative">
                    <img :src="img" class="h-16 w-16 rounded-lg border border-slate-200 object-cover" />
                    <button type="button" class="absolute -right-1 -top-1 flex h-4 w-4 items-center justify-center rounded-full bg-rose-500 text-white opacity-0 transition-opacity group-hover:opacity-100" @click="editImages.splice(idx, 1)">
                      <X class="h-2.5 w-2.5" />
                    </button>
                  </div>
                </div>
                <div class="mt-2 flex items-center gap-2">
                  <label class="inline-flex cursor-pointer items-center gap-1 rounded-md border border-slate-200 px-2 py-1 text-xs text-slate-500 hover:bg-slate-50">
                    <ImageIcon class="h-3.5 w-3.5" />
                    <input type="file" multiple accept="image/*" class="hidden" @change="uploadFiles(($event.target as HTMLInputElement).files, 'edit'); ($event.target as HTMLInputElement).value = ''" />
                    {{ uploading ? '업로드…' : '이미지' }}
                  </label>
                  <button type="button" :disabled="!editContent.trim() || editContent.length > MAX_LEN" class="rounded-md bg-emerald-500 px-2.5 py-1 text-xs font-medium text-white hover:bg-emerald-600 disabled:opacity-40" @click="saveEdit">저장</button>
                  <button type="button" class="rounded-md bg-slate-100 px-2.5 py-1 text-xs font-medium text-slate-600 hover:bg-slate-200" @click="cancelEdit">취소</button>
                  <span :class="['text-xs', editContent.length > MAX_LEN ? 'text-rose-500' : 'text-slate-400']">{{ editContent.length }}/{{ MAX_LEN }}</span>
                </div>
              </div>

              <!-- 표시 모드 -->
              <div v-else>
                <p class="mt-1 whitespace-pre-wrap text-sm leading-relaxed text-slate-600">
                  <template v-for="(seg, i) in renderSegments(root.content)" :key="i">
                    <span :class="seg.mention ? 'rounded bg-emerald-50 px-0.5 font-medium text-emerald-600' : ''">{{ seg.text }}</span>
                  </template>
                </p>
                <div v-if="root.images.length > 0" class="mt-2 flex flex-wrap gap-2">
                  <button v-for="(img, idx) in root.images" :key="img + idx" type="button" class="block cursor-zoom-in" @click="lightboxSrc = img">
                    <img :src="img" alt="" class="h-16 w-16 rounded-lg border border-slate-200 object-cover transition-colors hover:border-emerald-300 md:h-20 md:w-20" />
                  </button>
                </div>

                <!-- 반응 -->
                <div class="mt-2 flex flex-wrap items-center gap-1.5">
                  <button
                    v-for="emoji in existingReactionEmojis(root)"
                    :key="emoji"
                    type="button"
                    :class="['inline-flex items-center gap-1 whitespace-nowrap rounded-full border px-2 py-0.5 text-xs transition-colors',
                      hasReacted(root, emoji)
                        ? 'border-emerald-200 bg-emerald-100 text-emerald-600'
                        : 'border-slate-200 bg-slate-100 text-slate-600 hover:bg-slate-200']"
                    @click="onReaction(root.id, emoji)"
                  >
                    <span>{{ emoji }}</span>
                    <span class="font-medium">{{ reactionCount(root, emoji) }}</span>
                  </button>
                  <div class="relative">
                    <button type="button" class="inline-flex items-center rounded-full border border-slate-200 bg-slate-100 px-2 py-0.5 text-xs text-slate-400 hover:bg-slate-200" title="반응 추가" @click="reactionPickerId = (reactionPickerId === root.id ? null : root.id)">
                      <Plus class="h-3 w-3" />
                    </button>
                    <div v-if="reactionPickerId === root.id" ref="pickerRef" class="absolute bottom-full left-0 z-20 mb-1 flex gap-1 rounded-lg border border-slate-200 bg-white p-2 shadow-lg">
                      <button v-for="emoji in EMOJIS" :key="emoji" type="button" :class="['flex h-8 w-8 items-center justify-center rounded-md text-lg transition-colors hover:bg-slate-100', hasReacted(root, emoji) ? 'bg-emerald-50' : '']" @click="onReaction(root.id, emoji)">{{ emoji }}</button>
                    </div>
                  </div>
                </div>

                <!-- 액션 -->
                <div class="mt-1.5 flex items-center gap-2">
                  <button v-if="replyToId !== root.id" type="button" class="flex items-center gap-0.5 text-xs text-slate-400 hover:text-emerald-500" @click="startReply(root.id)">
                    <Reply class="h-3 w-3" /> 답글
                  </button>
                  <template v-if="isMine(root)">
                    <template v-if="deletingId === root.id">
                      <span class="text-xs text-rose-500">삭제할까요?</span>
                      <button type="button" class="text-xs font-medium text-rose-500 hover:text-rose-700" @click="confirmDelete(root.id)">확인</button>
                      <button type="button" class="text-xs font-medium text-slate-400 hover:text-slate-600" @click="deletingId = null">취소</button>
                    </template>
                    <template v-else>
                      <button type="button" class="flex items-center gap-0.5 text-xs text-slate-400 hover:text-slate-600" @click="startEdit(root)">
                        <Edit3 class="h-3 w-3" /> 수정
                      </button>
                      <button type="button" class="flex items-center gap-0.5 text-xs text-slate-400 hover:text-rose-500" @click="deletingId = root.id">
                        <Trash2 class="h-3 w-3" /> 삭제
                      </button>
                    </template>
                  </template>
                </div>
              </div>
            </div>
          </div>

          <!-- 답글 목록 -->
          <div v-if="(tree.childrenMap.get(root.id) ?? []).length > 0" class="ml-11 mt-3 space-y-3 border-l-2 border-slate-100 pl-4">
            <div v-for="child in tree.childrenMap.get(root.id) ?? []" :key="child.id" class="flex gap-3">
              <div class="shrink-0">
                <img v-if="child.author.avatarUrl" :src="child.author.avatarUrl" :alt="child.author.name" class="h-8 w-8 rounded-full bg-slate-100 object-cover" />
                <div v-else class="flex h-8 w-8 items-center justify-center rounded-full bg-emerald-100 text-sm font-medium text-emerald-600">{{ memberInitial(child.author.name) }}</div>
              </div>
              <div class="min-w-0 flex-1">
                <div class="flex flex-wrap items-center gap-2">
                  <CornerDownRight class="h-3 w-3 text-slate-300" />
                  <span class="text-sm font-medium text-slate-700">{{ child.author.name }}</span>
                  <span class="text-xs text-slate-400">{{ timeAgo(child.createdAt) }}</span>
                </div>
                <p class="mt-1 whitespace-pre-wrap text-sm leading-relaxed text-slate-600">
                  <template v-for="(seg, i) in renderSegments(child.content)" :key="i">
                    <span :class="seg.mention ? 'rounded bg-emerald-50 px-0.5 font-medium text-emerald-600' : ''">{{ seg.text }}</span>
                  </template>
                </p>
                <div v-if="child.images.length > 0" class="mt-2 flex flex-wrap gap-2">
                  <button v-for="(img, idx) in child.images" :key="img + idx" type="button" class="block cursor-zoom-in" @click="lightboxSrc = img">
                    <img :src="img" class="h-14 w-14 rounded-lg border border-slate-200 object-cover transition-colors hover:border-emerald-300" />
                  </button>
                </div>
                <div v-if="isMine(child)" class="mt-1.5 flex items-center gap-2">
                  <template v-if="deletingId === child.id">
                    <span class="text-xs text-rose-500">삭제할까요?</span>
                    <button type="button" class="text-xs font-medium text-rose-500 hover:text-rose-700" @click="confirmDelete(child.id)">확인</button>
                    <button type="button" class="text-xs font-medium text-slate-400 hover:text-slate-600" @click="deletingId = null">취소</button>
                  </template>
                  <template v-else>
                    <button type="button" class="flex items-center gap-0.5 text-xs text-slate-400 hover:text-rose-500" @click="deletingId = child.id">
                      <Trash2 class="h-3 w-3" /> 삭제
                    </button>
                  </template>
                </div>
              </div>
            </div>
          </div>

          <!-- 인라인 답글 입력 -->
          <div v-if="replyToId === root.id" class="ml-11 mt-3 border-l-2 border-slate-100 pl-4">
            <div class="flex gap-3">
              <div class="shrink-0">
                <img v-if="auth.user?.avatarUrl" :src="auth.user.avatarUrl" :alt="auth.user.name" class="h-8 w-8 rounded-full bg-slate-100 object-cover" />
                <div v-else class="flex h-8 w-8 items-center justify-center rounded-full bg-emerald-100 text-sm font-medium text-emerald-600">{{ memberInitial(auth.user?.name ?? '?') }}</div>
              </div>
              <div class="min-w-0 flex-1">
                <div class="relative">
                  <textarea
                    ref="replyRef"
                    v-model="replyContent"
                    rows="2"
                    :maxlength="MAX_LEN"
                    :placeholder="`${root.author.name}님에게 답글...`"
                    class="w-full resize-none rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-700 placeholder-slate-400 focus:border-emerald-300 focus:outline-none focus:ring-2 focus:ring-emerald-100"
                    @input="checkMention($event.target as HTMLTextAreaElement, 'reply')"
                    @keydown="onMentionKey"
                  />
                  <ul v-if="showMention && mentionMode === 'reply' && filteredMembers.length > 0" class="absolute left-0 top-full z-20 mt-1 max-h-48 w-52 overflow-y-auto rounded-lg border border-slate-200 bg-white shadow-lg">
                    <li v-for="(m, i) in filteredMembers" :key="m.id">
                      <button type="button" :class="['flex w-full items-center gap-2 px-3 py-2 text-left transition-colors hover:bg-slate-50', i === mentionIdx ? 'bg-emerald-50' : '']" @click="insertMention(m.name)">
                        <img v-if="m.avatarUrl" :src="m.avatarUrl" :alt="m.name" class="h-6 w-6 rounded-full object-cover" />
                        <div v-else class="flex h-6 w-6 items-center justify-center rounded-full bg-emerald-100 text-xs font-medium text-emerald-600">{{ memberInitial(m.name) }}</div>
                        <div class="min-w-0 flex-1">
                          <span class="block text-sm text-slate-700">{{ m.name }}</span>
                          <span class="block text-[10px] text-slate-400">{{ m.role ?? '' }}</span>
                        </div>
                      </button>
                    </li>
                  </ul>
                </div>
                <div v-if="replyImages.length > 0" class="mt-2 flex flex-wrap gap-2">
                  <div v-for="(img, idx) in replyImages" :key="img + idx" class="group relative">
                    <img :src="img" class="h-14 w-14 rounded-lg border border-slate-200 object-cover" />
                    <button type="button" class="absolute -right-1 -top-1 flex h-4 w-4 items-center justify-center rounded-full bg-rose-500 text-white opacity-0 transition-opacity group-hover:opacity-100" @click="replyImages.splice(idx, 1)">
                      <X class="h-2.5 w-2.5" />
                    </button>
                  </div>
                </div>
                <div class="mt-2 flex items-center gap-2">
                  <label class="inline-flex cursor-pointer items-center gap-1 rounded-md border border-slate-200 px-2 py-1 text-xs text-slate-500 hover:bg-slate-50">
                    <ImageIcon class="h-3.5 w-3.5" />
                    <input type="file" multiple accept="image/*" class="hidden" @change="uploadFiles(($event.target as HTMLInputElement).files, 'reply'); ($event.target as HTMLInputElement).value = ''" />
                    {{ uploading ? '업로드…' : '이미지' }}
                  </label>
                  <button type="button" :disabled="(!replyContent.trim() && replyImages.length === 0) || replyContent.length > MAX_LEN" class="rounded-md bg-emerald-500 px-2.5 py-1 text-xs font-medium text-white hover:bg-emerald-600 disabled:opacity-40" @click="submitReply">답글 작성</button>
                  <button type="button" class="rounded-md bg-slate-100 px-2.5 py-1 text-xs font-medium text-slate-600 hover:bg-slate-200" @click="cancelReply">취소</button>
                  <span :class="['text-xs', replyContent.length > MAX_LEN ? 'text-rose-500' : 'text-slate-400']">{{ replyContent.length }}/{{ MAX_LEN }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 새 댓글 작성 -->
    <div class="border-t border-slate-100 p-4 md:p-5">
      <form @submit.prevent="submitNew">
        <div class="flex items-start gap-3">
          <div class="shrink-0">
            <img v-if="auth.user?.avatarUrl" :src="auth.user.avatarUrl" :alt="auth.user.name" class="h-8 w-8 rounded-full bg-slate-100 object-cover" />
            <div v-else class="flex h-8 w-8 items-center justify-center rounded-full bg-emerald-100 text-sm font-medium text-emerald-600">{{ memberInitial(auth.user?.name ?? '?') }}</div>
          </div>
          <div class="min-w-0 flex-1">
            <div class="relative">
              <textarea
                ref="newRef"
                v-model="newContent"
                rows="2"
                :maxlength="MAX_LEN"
                placeholder="코멘트를 입력하세요... (@를 입력하면 멤버 멘션)"
                class="w-full resize-none rounded-lg border border-slate-200 bg-white px-3 py-2.5 text-sm text-slate-700 placeholder-slate-400 focus:border-emerald-300 focus:outline-none focus:ring-2 focus:ring-emerald-100"
                @input="checkMention($event.target as HTMLTextAreaElement, 'new')"
                @keydown="onMentionKey"
              />
              <ul v-if="showMention && mentionMode === 'new' && filteredMembers.length > 0" class="absolute left-0 top-full z-20 mt-1 max-h-48 w-52 overflow-y-auto rounded-lg border border-slate-200 bg-white shadow-lg">
                <li v-for="(m, i) in filteredMembers" :key="m.id">
                  <button type="button" :class="['flex w-full items-center gap-2 px-3 py-2 text-left transition-colors hover:bg-slate-50', i === mentionIdx ? 'bg-emerald-50' : '']" @click="insertMention(m.name)">
                    <img v-if="m.avatarUrl" :src="m.avatarUrl" :alt="m.name" class="h-6 w-6 rounded-full object-cover" />
                    <div v-else class="flex h-6 w-6 items-center justify-center rounded-full bg-emerald-100 text-xs font-medium text-emerald-600">{{ memberInitial(m.name) }}</div>
                    <div class="min-w-0 flex-1">
                      <span class="block text-sm text-slate-700">{{ m.name }}</span>
                      <span class="block text-[10px] text-slate-400">{{ m.role ?? '' }}</span>
                    </div>
                    <Check v-if="i === mentionIdx" class="h-3 w-3 text-emerald-500" />
                  </button>
                </li>
              </ul>
            </div>
            <div v-if="newImages.length > 0" class="mt-2 flex flex-wrap gap-2">
              <div v-for="(img, idx) in newImages" :key="img + idx" class="group relative">
                <img :src="img" class="h-16 w-16 rounded-lg border border-slate-200 object-cover" />
                <button type="button" class="absolute -right-1 -top-1 flex h-4 w-4 items-center justify-center rounded-full bg-rose-500 text-white opacity-0 transition-opacity group-hover:opacity-100" @click="newImages.splice(idx, 1)">
                  <X class="h-2.5 w-2.5" />
                </button>
              </div>
            </div>
            <div class="mt-2 flex items-center justify-between">
              <label class="inline-flex cursor-pointer items-center gap-1 rounded-md border border-slate-200 px-2 py-1 text-xs text-slate-500 hover:bg-slate-50">
                <ImageIcon class="h-3.5 w-3.5" />
                <input type="file" multiple accept="image/*" class="hidden" @change="uploadFiles(($event.target as HTMLInputElement).files, 'new'); ($event.target as HTMLInputElement).value = ''" />
                {{ uploading ? '업로드…' : '이미지 추가' }}
              </label>
              <div class="flex items-center gap-2">
                <span :class="['text-xs', newContent.length > MAX_LEN ? 'text-rose-500' : 'text-slate-400']">{{ newContent.length }}/{{ MAX_LEN }}</span>
                <button
                  type="submit"
                  :disabled="submitting || uploading || (!newContent.trim() && newImages.length === 0) || newContent.length > MAX_LEN"
                  class="rounded-lg bg-emerald-500 px-3 py-1.5 text-xs font-medium text-white hover:bg-emerald-600 disabled:cursor-not-allowed disabled:opacity-40"
                >{{ submitting ? '등록 중…' : '코멘트 작성' }}</button>
              </div>
            </div>
            <p v-if="error" class="mt-2 rounded bg-red-50 px-2 py-1 text-xs text-red-700">{{ error }}</p>
          </div>
        </div>
      </form>
    </div>

    <ImageLightbox :src="lightboxSrc" @close="lightboxSrc = null" />
  </section>
</template>
