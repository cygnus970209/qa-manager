<script setup lang="ts">
import { VueFlow, Handle, MarkerType, Position, useVueFlow } from '@vue-flow/core'
import type { Connection, Edge, EdgeChange, EdgeMouseEvent, Node, NodeChange, NodeMouseEvent } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import '@vue-flow/controls/dist/style.css'
import {
  ArrowLeft,
  Check,
  ChevronRight,
  CircleCheck,
  GitBranch,
  ImagePlus,
  ListChecks,
  LoaderCircle,
  Monitor,
  MousePointerClick,
  Plus,
  Save,
  Trash2,
  Workflow,
  X,
} from '@lucide/vue'
import { formatDate } from '~/utils/format'
import DeleteConfirmModal from '~/components/base/DeleteConfirmModal.vue'
import ImageLightbox from '~/components/base/ImageLightbox.vue'
import AppSelect from '~/components/base/AppSelect.vue'
import type { SelectOption } from '~/composables/useSelectOptions'
import TestFlowPathModal from '~/components/feature/testing/TestFlowPathModal.vue'
import type { FlowEdge, FlowGraph, FlowNode, FlowNodeType, ProjectUpdate, TestFlowSummary, TestSuite } from '~/types/api'

/**
 * 테스트 플로우(워크플로우) 그래프 편집 뷰.
 * VueFlow 내부 표현: node.type='custom' 하나 + data{ kind, label, expected }.
 * 저장 시 FlowGraph 계약(nodes[{id,type,label,expected?,position}], edges[{id,source,target,label?}])으로 직렬화.
 */
const props = defineProps<{ projectId: number; updates: ProjectUpdate[] }>()

const testing = useTesting()
const { t } = useI18n()
const { confirmDialog } = useAppDialog()

// 다크모드 여부 (캔버스 배경 dot 색상용) — vueuse useColorMode 와 이름이 겹쳐 모듈 주입 사용
const colorMode = useNuxtApp().$colorMode
const isDark = computed(() => colorMode.value === 'dark')

/** VueFlow 노드 data — 저장 시 FlowNode 로 역직렬화된다. */
interface EditorNodeData {
  kind: FlowNodeType
  label: string
  expected: string
  /** 참고 이미지 URL (없으면 빈 문자열) */
  image: string
}

const {
  addNodes,
  addEdges,
  removeNodes,
  removeEdges,
  findNode,
  findEdge,
  getNodes,
  getEdges,
  fitView,
  onNodesInitialized,
} = useVueFlow()

/* ─────────────── 상태 ─────────────── */
const flows = ref<TestFlowSummary[]>([])
const loadingFlows = ref(false)
const loadingFlow = ref(false)
const selectedFlowId = ref<number | null>(null)
const flowName = ref('')
const savedName = ref('')
const linkedUpdateId = ref<number | null>(null)

// ref<Node[]> 는 vue-flow 제네릭의 UnwrapRef 재귀로 TS2589 가 나므로 캐스팅으로 우회한다.
const nodes = ref([]) as Ref<Node[]>
const edges = ref([]) as Ref<Edge[]>

const dirty = ref(false)
const saving = ref(false)
const savedFlash = ref(false)
const error = ref<string | null>(null)

const selectedNodeId = ref<string | null>(null)
const selectedEdgeId = ref<string | null>(null)

const newFlowOpen = ref(false)
const newFlowName = ref('')
const creatingFlow = ref(false)

const deleteOpen = ref(false)

const pathModalOpen = ref(false)
const pathGraph = ref<FlowGraph | null>(null)
const suites = ref<TestSuite[]>([])
const suitesLoaded = ref(false)
const openingPathModal = ref(false)
const casesCreatedMsg = ref<string | null>(null)

let savedTimer: ReturnType<typeof setTimeout> | undefined
let createdTimer: ReturnType<typeof setTimeout> | undefined
// 그래프 로드/초기화 중 change 이벤트로 dirty 가 켜지는 것 방지
let suppressChanges = false
// 그래프 로드 직후 1회 fitView
let pendingFit = false

const projectUpdates = computed(() => props.updates.filter((u) => u.projectId === props.projectId))
const linkedUpdateOptions = computed<SelectOption<number | null>[]>(() => [
  { value: null, label: t('testflow.toolbar.noLinkedUpdate') },
  ...projectUpdates.value.map((u) => ({ value: u.id, label: `${u.version} · ${u.title}` })),
])

// 캔버스 스냅 그리드 (SnapGrid 튜플 타입)
const snapGrid: [number, number] = [10, 10]

function genId(prefix: string): string {
  return `${prefix}-${Date.now().toString(36)}${Math.random().toString(36).slice(2, 7)}`
}

/* ─────────────── FlowGraph ↔ VueFlow 변환 ─────────────── */
function toVueFlowGraph(graph: FlowGraph) {
  nodes.value = (graph.nodes ?? []).map((n) => ({
    id: n.id,
    type: 'custom',
    position: { x: n.position.x, y: n.position.y },
    // start/end 는 삭제 불가 (키보드 삭제 포함)
    deletable: n.type !== 'start' && n.type !== 'end',
    data: { kind: n.type, label: n.label, expected: n.expected ?? '', image: n.image ?? '' } satisfies EditorNodeData,
  }))
  edges.value = (graph.edges ?? []).map((e) => ({ id: e.id, source: e.source, target: e.target, label: e.label }))
}

function serializeGraph(): FlowGraph {
  // 마운트 후에는 VueFlow 내부 상태(최신 위치 포함)가 소스, 그 외엔 로컬 ref
  const vfNodes = getNodes.value.length > 0 ? getNodes.value : nodes.value
  const vfEdges = getNodes.value.length > 0 ? getEdges.value : edges.value
  return {
    nodes: vfNodes.map((n) => {
      const data = (n.data ?? {}) as Partial<EditorNodeData>
      const node: FlowNode = {
        id: n.id,
        type: data.kind ?? 'action',
        label: data.label ?? '',
        position: { x: Math.round(n.position.x), y: Math.round(n.position.y) },
      }
      const expected = data.expected?.trim()
      if (expected) node.expected = expected
      const image = data.image?.trim()
      if (image) node.image = image
      return node
    }),
    edges: vfEdges.map((e) => {
      const edge: FlowEdge = { id: e.id, source: e.source, target: e.target }
      const label = typeof e.label === 'string' ? e.label.trim() : ''
      if (label) edge.label = label
      return edge
    }),
  }
}

/* ─────────────── 플로우 목록/로드 ───────────────
 * 진입 구조: 목록(selectedFlowId == null) → 행 클릭으로 에디터 진입.
 * 프로젝트별 플로우가 많아질 수 있어 드롭다운 대신 목록 화면을 1차 뷰로 둔다. */
async function loadFlows(selectId?: number) {
  loadingFlows.value = true
  error.value = null
  try {
    flows.value = await testing.listFlows(props.projectId)
    if (selectId != null && flows.value.some((f) => f.id === selectId)) {
      await loadFlow(selectId)
    }
  } catch (e: any) {
    error.value = e?.data?.message ?? t('testflow.messages.loadFailed')
  } finally {
    loadingFlows.value = false
  }
}

/** 에디터 → 목록 복귀 (미저장 변경은 확인 후 폐기) */
async function backToList() {
  // window.confirm 은 데스크톱(웹뷰)에서 동작하지 않는다 — 앱 내 다이얼로그 사용
  if (dirty.value && !(await confirmDialog({ message: t('testflow.messages.discardConfirm') }))) return
  clearEditor()
}

async function loadFlow(id: number) {
  loadingFlow.value = true
  error.value = null
  try {
    const f = await testing.getFlow(id)
    selectedFlowId.value = f.id
    flowName.value = f.name
    savedName.value = f.name
    linkedUpdateId.value = f.updateId
    suppressChanges = true
    pendingFit = true
    toVueFlowGraph(f.graph ?? { nodes: [], edges: [] })
    selectedNodeId.value = null
    selectedEdgeId.value = null
    dirty.value = false
    await nextTick()
    await nextTick()
    suppressChanges = false
    resetHistory()
    present = serializeGraph()
  } catch (e: any) {
    error.value = e?.data?.message ?? t('testflow.messages.loadFailed')
  } finally {
    loadingFlow.value = false
  }
}

function clearEditor() {
  selectedFlowId.value = null
  flowName.value = ''
  savedName.value = ''
  linkedUpdateId.value = null
  suppressChanges = true
  nodes.value = []
  edges.value = []
  dirty.value = false
  selectedNodeId.value = null
  selectedEdgeId.value = null
  resetHistory()
  void nextTick(() => { suppressChanges = false })
}

/** 목록에서 플로우 열기 */
async function openFlow(id: number) {
  if (loadingFlow.value) return
  await loadFlow(id)
}

/* ─────────────── 플로우 생성/이름 변경/연결/삭제 ─────────────── */
async function createNewFlow() {
  const name = newFlowName.value.trim()
  if (!name || creatingFlow.value) return
  if (dirty.value && !(await confirmDialog({ message: t('testflow.messages.discardConfirm') }))) return
  creatingFlow.value = true
  error.value = null
  try {
    const created = await testing.createFlow(props.projectId, { name })
    // 새 플로우: 시작/종료 노드 자동 배치 후 곧바로 저장
    const graph: FlowGraph = {
      nodes: [
        { id: genId('n'), type: 'start', label: t('testflow.node.start'), position: { x: 80, y: 200 } },
        { id: genId('n'), type: 'end', label: t('testflow.node.end'), position: { x: 720, y: 200 } },
      ],
      edges: [],
    }
    await testing.updateFlow(created.id, { graph })
    flows.value = [...flows.value, {
      id: created.id,
      projectId: created.projectId,
      updateId: created.updateId,
      name: created.name,
      updatedAt: created.updatedAt,
    }]
    newFlowOpen.value = false
    newFlowName.value = ''
    await loadFlow(created.id)
  } catch (e: any) {
    error.value = e?.data?.message ?? t('testflow.messages.createFailed')
  } finally {
    creatingFlow.value = false
  }
}

async function commitRename() {
  if (selectedFlowId.value == null) return
  const name = flowName.value.trim()
  if (!name || name === savedName.value) {
    flowName.value = savedName.value
    return
  }
  try {
    await testing.updateFlow(selectedFlowId.value, { name })
    savedName.value = name
    flowName.value = name
    const f = flows.value.find((x) => x.id === selectedFlowId.value)
    if (f) f.name = name
  } catch (e: any) {
    flowName.value = savedName.value
    error.value = e?.data?.message ?? t('testflow.messages.renameFailed')
  }
}

async function onUpdateSelChange(id: number | null) {
  if (selectedFlowId.value == null) return
  const prev = linkedUpdateId.value
  linkedUpdateId.value = id
  try {
    // 백엔드 규약: updateId=0 이면 연결 해제
    await testing.updateFlow(selectedFlowId.value, { updateId: id ?? 0 })
    const f = flows.value.find((x) => x.id === selectedFlowId.value)
    if (f) f.updateId = id
  } catch (err: any) {
    linkedUpdateId.value = prev
    error.value = err?.data?.message ?? t('testflow.messages.linkUpdateFailed')
  }
}

async function confirmDeleteFlow() {
  const id = selectedFlowId.value
  if (id == null) return
  try {
    await testing.removeFlow(id)
    flows.value = flows.value.filter((f) => f.id !== id)
    deleteOpen.value = false
    clearEditor() // 삭제 후 목록으로 복귀
  } catch (e: any) {
    deleteOpen.value = false
    error.value = e?.data?.message ?? t('testflow.messages.deleteFailed')
  }
}

/* ─────────────── 캔버스 편집 ─────────────── */
function markDirty() {
  if (suppressChanges) return
  dirty.value = true
  savedFlash.value = false
  scheduleHistory()
}

/* ─────────────── 실행 취소/다시 실행 (그래프 스냅샷 히스토리) ───────────────
 * 모든 변경은 markDirty 를 거치므로 여기서 디바운스로 스냅샷을 쌓는다.
 * present = 마지막으로 확정된 상태(연속 입력 burst 이전) → undo 시 그 상태로 복원. */
const HISTORY_MAX = 50
const history = ref<FlowGraph[]>([])
const future = ref<FlowGraph[]>([])
let present: FlowGraph | null = null
let historyTimer: ReturnType<typeof setTimeout> | undefined
let historyPending = false

function resetHistory() {
  clearTimeout(historyTimer)
  historyPending = false
  history.value = []
  future.value = []
  present = null
}

function flushHistory() {
  if (!historyPending) return
  clearTimeout(historyTimer)
  historyPending = false
  if (present) {
    history.value.push(present)
    if (history.value.length > HISTORY_MAX) history.value.shift()
  }
  present = serializeGraph()
  future.value = []
}

/** 연속 입력(타이핑·드래그 후속 이벤트)은 350ms 디바운스로 1건으로 합친다. */
function scheduleHistory() {
  historyPending = true
  clearTimeout(historyTimer)
  historyTimer = setTimeout(flushHistory, 350)
}

function applySnapshot(snap: FlowGraph) {
  suppressChanges = true
  toVueFlowGraph(snap)
  selectedNodeId.value = null
  selectedEdgeId.value = null
  dirty.value = true
  savedFlash.value = false
  void nextTick().then(() => nextTick()).then(() => { suppressChanges = false })
}

function undo() {
  flushHistory() // 아직 확정되지 않은 burst 를 먼저 히스토리에 반영
  const snap = history.value.pop()
  if (!snap) return
  if (present) future.value.push(present)
  present = snap
  applySnapshot(snap)
}

function redo() {
  flushHistory()
  const snap = future.value.pop()
  if (!snap) return
  if (present) history.value.push(present)
  present = snap
  applySnapshot(snap)
}

/* ─────────────── 노드 복사/붙여넣기 ─────────────── */
let clipboard: { kind: FlowNodeType; label: string; expected: string; image: string; position: { x: number; y: number } } | null = null
let pasteCount = 0

function copySelectedNode() {
  const n = selectedNode.value
  const d = selectedNodeData.value
  if (!n || !d || d.kind === 'start' || d.kind === 'end') return
  clipboard = { kind: d.kind, label: d.label, expected: d.expected, image: d.image ?? '', position: { x: n.position.x, y: n.position.y } }
  pasteCount = 0
}

function pasteNode() {
  if (!clipboard || selectedFlowId.value == null) return
  pasteCount += 1
  const id = genId('n')
  addNodes([{
    id,
    type: 'custom',
    position: { x: clipboard.position.x + 28 * pasteCount, y: clipboard.position.y + 28 * pasteCount },
    deletable: true,
    data: { kind: clipboard.kind, label: clipboard.label, expected: clipboard.expected, image: clipboard.image } satisfies EditorNodeData,
  }])
  selectedNodeId.value = id
  selectedEdgeId.value = null
  markDirty()
}

/* ─────────────── 노드 이미지 (화면 시안/스크린샷 첨부) ─────────────── */
const upload = useUpload()
const nodeImageInput = ref<HTMLInputElement | null>(null)
const uploadingImage = ref(false)
const lightboxSrc = ref<string | null>(null)

async function onNodeImagePick(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file || uploadingImage.value) return
  uploadingImage.value = true
  error.value = null
  try {
    const url = await upload.uploadFile(file, 'qa_image')
    patchSelectedNode({ image: url })
  } catch (err: any) {
    error.value = err?.data?.message ?? err?.message ?? t('testflow.messages.imageUploadFailed')
  } finally {
    uploadingImage.value = false
  }
}

/* ─────────────── 에디터 단축키 (Del 삭제 · Ctrl+Z/Y · Ctrl+C/V) ─────────────── */
function isTypingTarget(el: EventTarget | null): boolean {
  const t = el as HTMLElement | null
  return !!t && (t.tagName === 'INPUT' || t.tagName === 'TEXTAREA' || t.tagName === 'SELECT' || t.isContentEditable)
}

function onEditorKeydown(e: KeyboardEvent) {
  if (selectedFlowId.value == null || pathModalOpen.value || deleteOpen.value || loadingFlow.value) return
  if (isTypingTarget(e.target)) return
  const mod = e.metaKey || e.ctrlKey
  if (!mod && (e.key === 'Delete' || e.key === 'Backspace')) {
    if (selectedEdgeId.value) {
      removeSelectedEdge()
      e.preventDefault()
    } else if (selectedNodeId.value) {
      removeSelectedNode()
      e.preventDefault()
    }
    return
  }
  if (!mod) return
  const k = e.key.toLowerCase()
  if (k === 'z') {
    e.preventDefault()
    if (e.shiftKey) redo()
    else undo()
  } else if (k === 'y') {
    e.preventDefault()
    redo()
  } else if (k === 'c') {
    // 노드 미선택 시에는 기본 텍스트 복사 동작 유지
    if (selectedNodeId.value) {
      copySelectedNode()
      e.preventDefault()
    }
  } else if (k === 'v') {
    if (clipboard) {
      pasteNode()
      e.preventDefault()
    }
  }
}

function addPaletteNode(kind: 'screen' | 'action' | 'decision') {
  if (selectedFlowId.value == null) return
  // 캔버스 중앙 부근 + 랜덤 오프셋
  const position = {
    x: 380 + Math.round((Math.random() - 0.5) * 160),
    y: 200 + Math.round((Math.random() - 0.5) * 120),
  }
  const id = genId('n')
  addNodes([{
    id,
    type: 'custom',
    position,
    deletable: true,
    data: { kind, label: t(`testflow.palette.defaultLabel.${kind}`), expected: '', image: '' } satisfies EditorNodeData,
  }])
  selectedNodeId.value = id
  selectedEdgeId.value = null
  markDirty()
}

function onConnect(conn: Connection) {
  if (!conn.source || !conn.target || conn.source === conn.target) return
  // 동일 방향 중복 연결 방지
  const dup = [...getEdges.value, ...edges.value].some((e) => e.source === conn.source && e.target === conn.target)
  if (dup) return
  addEdges([{ id: genId('e'), source: conn.source, target: conn.target }])
  markDirty()
}

function onNodesChange(changes: NodeChange[]) {
  if (suppressChanges) return
  for (const c of changes) {
    if (c.type === 'remove') {
      if (selectedNodeId.value === c.id) selectedNodeId.value = null
      markDirty()
    } else if (c.type === 'position' && c.dragging === false) {
      markDirty()
    }
  }
}

function onEdgesChange(changes: EdgeChange[]) {
  if (suppressChanges) return
  for (const c of changes) {
    if (c.type === 'remove') {
      if (selectedEdgeId.value === c.id) selectedEdgeId.value = null
      markDirty()
    }
  }
}

function onNodeClick(e: NodeMouseEvent) {
  selectedNodeId.value = e.node.id
  selectedEdgeId.value = null
}

function onEdgeClick(e: EdgeMouseEvent) {
  selectedEdgeId.value = e.edge.id
  selectedNodeId.value = null
}

function onPaneClick() {
  selectedNodeId.value = null
  selectedEdgeId.value = null
}

const selectedNode = computed(() => {
  if (!selectedNodeId.value) return null
  return findNode(selectedNodeId.value) ?? nodes.value.find((n) => n.id === selectedNodeId.value) ?? null
})
const selectedNodeData = computed(() => (selectedNode.value?.data ?? null) as EditorNodeData | null)

const selectedEdge = computed(() => {
  if (!selectedEdgeId.value) return null
  return findEdge(selectedEdgeId.value) ?? edges.value.find((e) => e.id === selectedEdgeId.value) ?? null
})
const selectedEdgeLabel = computed(() => (typeof selectedEdge.value?.label === 'string' ? selectedEdge.value.label : ''))

/** 선택 엣지의 출발 → 도착 노드 라벨 (패널 컨텍스트 표기) */
const edgeContext = computed(() => {
  const e = selectedEdge.value
  if (!e) return ''
  const nodeLabel = (id: string) => {
    const n = findNode(id) ?? nodes.value.find((x) => x.id === id)
    return ((n?.data ?? null) as EditorNodeData | null)?.label ?? id
  }
  return `${nodeLabel(e.source)} → ${nodeLabel(e.target)}`
})

function patchSelectedNode(patch: Partial<EditorNodeData>) {
  const n = selectedNode.value
  if (!n) return
  n.data = { ...(n.data as EditorNodeData), ...patch }
  markDirty()
}

function setSelectedEdgeLabel(v: string) {
  const e = selectedEdge.value
  if (!e) return
  e.label = v
  markDirty()
}

function removeSelectedNode() {
  const n = selectedNode.value
  if (!n) return
  const kind = ((n.data ?? null) as EditorNodeData | null)?.kind
  if (kind === 'start' || kind === 'end') return
  removeNodes([n.id]) // 연결된 엣지도 함께 제거됨
  selectedNodeId.value = null
}

function removeSelectedEdge() {
  if (!selectedEdgeId.value) return
  removeEdges([selectedEdgeId.value])
  selectedEdgeId.value = null
}

/* ─────────────── 저장 / 케이스 생성 ─────────────── */
async function saveGraph(): Promise<boolean> {
  if (selectedFlowId.value == null || saving.value) return false
  saving.value = true
  error.value = null
  try {
    await testing.updateFlow(selectedFlowId.value, { graph: serializeGraph() })
    dirty.value = false
    savedFlash.value = true
    clearTimeout(savedTimer)
    savedTimer = setTimeout(() => { savedFlash.value = false }, 2500)
    return true
  } catch (e: any) {
    error.value = e?.data?.message ?? t('testflow.messages.saveFailed')
    return false
  } finally {
    saving.value = false
  }
}

async function openPathModal() {
  if (selectedFlowId.value == null || openingPathModal.value) return
  openingPathModal.value = true
  try {
    // 미저장 변경이 있으면 먼저 저장 — 저장 그래프와 생성 케이스의 불일치 방지
    if (dirty.value && !(await saveGraph())) return
    if (!suitesLoaded.value) {
      suites.value = await testing.listSuites(props.projectId)
      suitesLoaded.value = true
    }
    pathGraph.value = serializeGraph()
    pathModalOpen.value = true
  } catch (e: any) {
    error.value = e?.data?.message ?? t('testflow.messages.loadFailed')
  } finally {
    openingPathModal.value = false
  }
}

function onCasesCreated(count: number) {
  suitesLoaded.value = false // 모달에서 새 스위트가 생겼을 수 있어 다음에 다시 로드
  casesCreatedMsg.value = t('testflow.messages.casesCreated', { count }, count)
  clearTimeout(createdTimer)
  createdTimer = setTimeout(() => { casesCreatedMsg.value = null }, 3000)
}

/* ─────────────── 노드 렌더 헬퍼 ─────────────── */
function kindIcon(kind: FlowNodeType) {
  return kind === 'screen' ? Monitor : kind === 'action' ? MousePointerClick : kind === 'decision' ? GitBranch : null
}

function nodeClass(kind: FlowNodeType): string {
  switch (kind) {
    case 'start':
      return 'rounded-full bg-emerald-500 px-4 py-1.5 text-white shadow-sm dark:bg-emerald-600'
    case 'end':
      return 'rounded-full bg-slate-500 px-4 py-1.5 text-white shadow-sm dark:bg-slate-600'
    case 'screen':
      return 'rounded-lg border border-blue-200 bg-blue-50 px-3 py-2 text-blue-800 shadow-sm dark:border-blue-500/40 dark:bg-blue-500/15 dark:text-blue-300'
    case 'action':
      return 'rounded-lg border border-violet-200 bg-violet-50 px-3 py-2 text-violet-800 shadow-sm dark:border-violet-500/40 dark:bg-violet-500/15 dark:text-violet-300'
    case 'decision':
      return 'rounded-xl border border-amber-300 bg-amber-50 px-3 py-2 text-amber-800 shadow-sm dark:border-amber-500/40 dark:bg-amber-500/15 dark:text-amber-300'
  }
}

onNodesInitialized(() => {
  if (!pendingFit) return
  pendingFit = false
  void fitView({ padding: 0.2 })
})

watch(() => props.projectId, () => {
  clearEditor()
  flows.value = []
  suites.value = []
  suitesLoaded.value = false
  void loadFlows()
})

onMounted(() => {
  void loadFlows()
  window.addEventListener('keydown', onEditorKeydown)
})

onBeforeUnmount(() => {
  clearTimeout(savedTimer)
  clearTimeout(createdTimer)
  clearTimeout(historyTimer)
  window.removeEventListener('keydown', onEditorKeydown)
})
</script>

<template>
  <div class="space-y-3">
    <!-- 첫 로딩 -->
    <div
      v-if="loadingFlows && flows.length === 0"
      class="flex h-[560px] items-center justify-center rounded-xl border border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-900"
    >
      <LoaderCircle class="h-5 w-5 animate-spin text-slate-400 dark:text-slate-500" />
    </div>

    <!-- 목록 뷰: 플로우 선택 → 에디터 진입 -->
    <div
      v-else-if="selectedFlowId == null"
      class="overflow-hidden rounded-xl border border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-900"
    >
      <div class="flex flex-wrap items-center justify-between gap-2 border-b border-slate-100 px-4 py-3 dark:border-slate-800">
        <h3 class="flex items-center gap-2 text-sm font-semibold text-slate-700 dark:text-slate-200">
          <Workflow class="h-4 w-4 text-slate-400 dark:text-slate-500" />
          {{ $t('testflow.list.title') }}
          <span class="text-xs font-normal tabular-nums text-slate-400 dark:text-slate-500">{{ flows.length }}</span>
        </h3>
        <div v-if="newFlowOpen" class="flex items-center gap-2">
          <input
            v-model="newFlowName"
            type="text"
            maxlength="150"
            :placeholder="$t('testflow.toolbar.newFlowNamePlaceholder')"
            class="w-48 rounded-md border border-slate-300 px-3 py-1.5 text-xs focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100 dark:placeholder-slate-500"
            @keydown.enter.prevent="createNewFlow"
            @keydown.esc="newFlowOpen = false"
          />
          <button
            type="button"
            :disabled="creatingFlow || !newFlowName.trim()"
            class="rounded-md bg-emerald-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-emerald-700 disabled:opacity-60"
            @click="createNewFlow"
          >{{ creatingFlow ? $t('common.state.processing') : $t('common.actions.create') }}</button>
          <button
            type="button"
            class="rounded-md border border-slate-200 px-3 py-1.5 text-xs text-slate-600 hover:bg-slate-50 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-800/60"
            @click="newFlowOpen = false; newFlowName = ''"
          >{{ $t('common.actions.cancel') }}</button>
        </div>
        <button
          v-else
          type="button"
          class="flex items-center gap-1.5 rounded-md bg-emerald-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-emerald-700"
          @click="newFlowOpen = true"
        >
          <Plus class="h-3.5 w-3.5" />{{ $t('testflow.toolbar.newFlow') }}
        </button>
      </div>

      <!-- 빈 상태 -->
      <div v-if="flows.length === 0" class="flex flex-col items-center justify-center gap-1.5 px-6 py-16 text-center">
        <Workflow class="h-10 w-10 text-slate-300 dark:text-slate-600" />
        <p class="mt-2 text-sm font-semibold text-slate-600 dark:text-slate-300">{{ $t('testflow.empty.title') }}</p>
        <p class="text-xs text-slate-400 dark:text-slate-500">{{ $t('testflow.empty.description') }}</p>
      </div>

      <!-- 플로우 테이블 -->
      <table v-else class="w-full text-left text-sm">
        <thead class="border-b border-slate-100 bg-slate-50 dark:border-slate-800 dark:bg-slate-800/50">
          <tr>
            <th class="px-4 py-2.5 text-xs font-semibold text-slate-500 dark:text-slate-400">{{ $t('testflow.list.nameCol') }}</th>
            <th class="w-56 px-4 py-2.5 text-xs font-semibold text-slate-500 dark:text-slate-400">{{ $t('testflow.list.linkedCol') }}</th>
            <th class="w-28 px-4 py-2.5 text-xs font-semibold text-slate-500 dark:text-slate-400">{{ $t('testflow.list.updatedCol') }}</th>
            <th class="w-10 px-4 py-2.5" />
          </tr>
        </thead>
        <tbody class="divide-y divide-slate-100 dark:divide-slate-800">
          <tr
            v-for="f in flows"
            :key="f.id"
            class="cursor-pointer transition hover:bg-slate-50 dark:hover:bg-slate-800/60"
            @click="openFlow(f.id)"
          >
            <td class="px-4 py-3">
              <span class="font-medium text-slate-800 dark:text-slate-100">{{ f.name }}</span>
            </td>
            <td class="px-4 py-3 text-xs text-slate-500 dark:text-slate-400">
              <template v-if="f.updateId != null && projectUpdates.some((u) => u.id === f.updateId)">
                {{ projectUpdates.find((u) => u.id === f.updateId)!.version }} · {{ projectUpdates.find((u) => u.id === f.updateId)!.title }}
              </template>
              <span v-else class="text-slate-400 dark:text-slate-500">{{ $t('testflow.toolbar.noLinkedUpdate') }}</span>
            </td>
            <td class="px-4 py-3 text-xs tabular-nums text-slate-400 dark:text-slate-500">{{ formatDate(f.updatedAt) }}</td>
            <td class="px-4 py-3 text-right">
              <ChevronRight class="h-4 w-4 text-slate-300 dark:text-slate-600" />
            </td>
          </tr>
        </tbody>
      </table>
      <p v-if="error" class="border-t border-slate-100 px-4 py-2 text-xs text-red-600 dark:border-slate-800 dark:text-red-400">{{ error }}</p>
    </div>

    <template v-else>
      <!-- 상단 툴바 -->
      <div class="rounded-xl border border-slate-200 bg-white p-3 dark:border-slate-800 dark:bg-slate-900">
        <div class="flex flex-wrap items-center gap-2">
          <!-- 목록으로 -->
          <button
            type="button"
            class="flex items-center gap-1 rounded-md border border-slate-200 px-2.5 py-1.5 text-xs font-medium text-slate-600 hover:bg-slate-50 dark:border-slate-700 dark:text-slate-300 dark:hover:bg-slate-800/60"
            @click="backToList"
          >
            <ArrowLeft class="h-3.5 w-3.5" />{{ $t('testflow.toolbar.backToList') }}
          </button>

          <template v-if="selectedFlowId != null">
            <!-- 이름 변경 (Enter/blur 저장) -->
            <input
              v-model="flowName"
              type="text"
              maxlength="150"
              :title="$t('testflow.toolbar.renameTitle')"
              :placeholder="$t('testflow.toolbar.newFlowNamePlaceholder')"
              class="w-40 rounded-md border border-slate-300 px-2.5 py-1.5 text-xs focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100 dark:placeholder-slate-500"
              @blur="commitRename"
              @keydown.enter.prevent="($event.target as HTMLInputElement).blur()"
            />

            <!-- 연결 업데이트 -->
            <AppSelect
              class="max-w-[220px]"
              size="sm"
              :title="$t('testflow.toolbar.linkedUpdate')"
              :model-value="linkedUpdateId"
              :options="linkedUpdateOptions"
              @update:model-value="onUpdateSelChange"
            />

            <!-- 삭제 -->
            <button
              type="button"
              :title="$t('testflow.toolbar.deleteFlow')"
              :aria-label="$t('testflow.toolbar.deleteFlow')"
              class="rounded-md border border-slate-200 p-1.5 text-slate-400 hover:border-red-200 hover:bg-red-50 hover:text-red-500 dark:border-slate-700 dark:text-slate-500 dark:hover:border-red-500/40 dark:hover:bg-red-500/10 dark:hover:text-red-400"
              @click="deleteOpen = true"
            >
              <Trash2 class="h-4 w-4" />
            </button>
          </template>

          <!-- 우측: 상태 표시 + 저장/케이스 생성 -->
          <div class="ml-auto flex items-center gap-2">
            <span v-if="casesCreatedMsg" class="text-xs text-emerald-600 dark:text-emerald-400">{{ casesCreatedMsg }}</span>
            <span v-else-if="savedFlash" class="flex items-center gap-1 text-xs text-emerald-600 dark:text-emerald-400">
              <Check class="h-3.5 w-3.5" />{{ $t('testflow.toolbar.saved') }}
            </span>
            <span v-if="error" class="max-w-[240px] truncate text-xs text-red-600 dark:text-red-400" :title="error">{{ error }}</span>
            <button
              type="button"
              :disabled="!dirty || saving || selectedFlowId == null"
              :class="[
                'flex items-center gap-1.5 rounded-md px-3 py-1.5 text-xs font-medium transition-colors disabled:opacity-60',
                dirty
                  ? 'bg-emerald-600 text-white hover:bg-emerald-700'
                  : 'border border-slate-200 text-slate-500 dark:border-slate-700 dark:text-slate-400',
              ]"
              @click="saveGraph"
            >
              <Save class="h-3.5 w-3.5" />{{ saving ? $t('common.state.saving') : $t('common.actions.save') }}
            </button>
            <button
              type="button"
              :disabled="selectedFlowId == null || openingPathModal || loadingFlow"
              class="flex items-center gap-1.5 rounded-md border border-indigo-200 bg-indigo-50 px-3 py-1.5 text-xs font-medium text-indigo-700 hover:bg-indigo-100 disabled:opacity-60 dark:border-indigo-500/40 dark:bg-indigo-500/10 dark:text-indigo-300 dark:hover:bg-indigo-500/20"
              @click="openPathModal"
            >
              <LoaderCircle v-if="openingPathModal" class="h-3.5 w-3.5 animate-spin" />
              <ListChecks v-else class="h-3.5 w-3.5" />
              {{ $t('testflow.toolbar.generateCases') }}
            </button>
          </div>
        </div>
      </div>

      <div class="flex flex-col gap-3 lg:flex-row">
        <div class="min-w-0 flex-1">
          <!-- 노드 팔레트 -->
          <div class="mb-2 flex flex-wrap items-center gap-2">
            <button
              type="button"
              :disabled="selectedFlowId == null || loadingFlow"
              class="flex items-center gap-1.5 rounded-md border border-blue-200 bg-blue-50 px-2.5 py-1.5 text-xs font-medium text-blue-700 hover:bg-blue-100 disabled:opacity-50 dark:border-blue-500/40 dark:bg-blue-500/10 dark:text-blue-300 dark:hover:bg-blue-500/20"
              @click="addPaletteNode('screen')"
            >
              <Monitor class="h-3.5 w-3.5" />{{ $t('testflow.palette.addScreen') }}
            </button>
            <button
              type="button"
              :disabled="selectedFlowId == null || loadingFlow"
              class="flex items-center gap-1.5 rounded-md border border-violet-200 bg-violet-50 px-2.5 py-1.5 text-xs font-medium text-violet-700 hover:bg-violet-100 disabled:opacity-50 dark:border-violet-500/40 dark:bg-violet-500/10 dark:text-violet-300 dark:hover:bg-violet-500/20"
              @click="addPaletteNode('action')"
            >
              <MousePointerClick class="h-3.5 w-3.5" />{{ $t('testflow.palette.addAction') }}
            </button>
            <button
              type="button"
              :disabled="selectedFlowId == null || loadingFlow"
              class="flex items-center gap-1.5 rounded-md border border-amber-300 bg-amber-50 px-2.5 py-1.5 text-xs font-medium text-amber-700 hover:bg-amber-100 disabled:opacity-50 dark:border-amber-500/40 dark:bg-amber-500/10 dark:text-amber-300 dark:hover:bg-amber-500/20"
              @click="addPaletteNode('decision')"
            >
              <GitBranch class="h-3.5 w-3.5" />{{ $t('testflow.palette.addDecision') }}
            </button>
          </div>

          <!-- 캔버스 -->
          <div class="relative h-[560px] overflow-hidden rounded-xl border border-slate-200 bg-slate-50 dark:border-slate-800 dark:bg-slate-950">
            <ClientOnly>
              <VueFlow
                v-model:nodes="nodes"
                v-model:edges="edges"
                class="h-full w-full"
                :default-edge-options="{ type: 'smoothstep', markerEnd: MarkerType.ArrowClosed }"
                :min-zoom="0.25"
                :max-zoom="2"
                :snap-to-grid="true"
                :snap-grid="snapGrid"
                :delete-key-code="null"
                fit-view-on-init
                @connect="onConnect"
                @node-click="onNodeClick"
                @edge-click="onEdgeClick"
                @pane-click="onPaneClick"
                @nodes-change="onNodesChange"
                @edges-change="onEdgesChange"
              >
                <Background :gap="20" :size="1.5" :pattern-color="isDark ? '#334155' : '#cbd5e1'" />
                <Controls />

                <!-- 커스텀 노드: 타입별 색/아이콘 -->
                <template #node-custom="p">
                  <div
                    class="relative text-xs"
                    :class="[
                      nodeClass(p.data.kind),
                      selectedNodeId === p.id ? 'ring-2 ring-emerald-500 ring-offset-1 ring-offset-slate-50 dark:ring-emerald-400 dark:ring-offset-slate-950' : '',
                    ]"
                  >
                    <Handle v-if="p.data.kind !== 'start'" type="target" :position="Position.Left" />
                    <Handle v-if="p.data.kind !== 'end'" type="source" :position="Position.Right" />
                    <!-- 참고 이미지 썸네일 (클릭 시 라이트박스) -->
                    <img
                      v-if="p.data.image"
                      :src="p.data.image"
                      :alt="p.data.label"
                      draggable="false"
                      class="mb-1.5 h-16 w-40 cursor-zoom-in rounded object-cover ring-1 ring-black/10 dark:ring-white/10"
                      @click.stop="lightboxSrc = p.data.image"
                    />
                    <div class="flex items-center gap-1.5">
                      <component :is="kindIcon(p.data.kind)" v-if="kindIcon(p.data.kind)" class="h-3.5 w-3.5 shrink-0" />
                      <span class="max-w-[150px] truncate font-medium">{{ p.data.label }}</span>
                      <CircleCheck v-if="p.data.expected" class="h-3 w-3 shrink-0 opacity-70" />
                    </div>
                  </div>
                </template>
              </VueFlow>
              <template #fallback>
                <div class="flex h-full items-center justify-center text-xs text-slate-400 dark:text-slate-500">
                  {{ $t('common.state.loading') }}
                </div>
              </template>
            </ClientOnly>

            <!-- 플로우 전환 로딩 오버레이 -->
            <div v-if="loadingFlow" class="absolute inset-0 z-10 flex items-center justify-center bg-white/60 dark:bg-slate-950/60">
              <LoaderCircle class="h-5 w-5 animate-spin text-slate-400 dark:text-slate-500" />
            </div>
          </div>

          <!-- 단축키 안내 -->
          <p class="mt-1.5 text-[11px] text-slate-400 dark:text-slate-500">{{ $t('testflow.shortcuts.hint') }}</p>
        </div>

        <!-- 선택 노드/엣지 편집 패널 -->
        <aside class="w-full shrink-0 lg:w-72">
          <div class="rounded-xl border border-slate-200 bg-white p-4 dark:border-slate-800 dark:bg-slate-900">
            <template v-if="selectedNode && selectedNodeData">
              <div class="mb-3 flex items-center justify-between">
                <h3 class="text-xs font-semibold text-slate-700 dark:text-slate-200">{{ $t('testflow.panel.nodeTitle') }}</h3>
                <span class="rounded-full bg-slate-100 px-2 py-0.5 text-[10px] font-medium text-slate-500 dark:bg-slate-800 dark:text-slate-400">
                  {{ $t(`testflow.node.${selectedNodeData.kind}`) }}
                </span>
              </div>
              <label class="block">
                <span class="block text-xs font-medium text-slate-600 dark:text-slate-300">{{ $t('testflow.panel.labelLabel') }}</span>
                <input
                  :value="selectedNodeData.label"
                  type="text"
                  maxlength="100"
                  class="mt-1 w-full rounded-md border border-slate-300 px-3 py-1.5 text-xs focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100 dark:placeholder-slate-500"
                  @input="patchSelectedNode({ label: ($event.target as HTMLInputElement).value })"
                />
              </label>
              <label v-if="selectedNodeData.kind !== 'start' && selectedNodeData.kind !== 'end'" class="mt-3 block">
                <span class="block text-xs font-medium text-slate-600 dark:text-slate-300">{{ $t('testflow.panel.expectedLabel') }}</span>
                <textarea
                  :value="selectedNodeData.expected"
                  rows="3"
                  maxlength="1000"
                  :placeholder="$t('testflow.panel.expectedPlaceholder')"
                  class="mt-1 w-full resize-none rounded-md border border-slate-300 px-3 py-1.5 text-xs focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100 dark:placeholder-slate-500"
                  @input="patchSelectedNode({ expected: ($event.target as HTMLTextAreaElement).value })"
                />
              </label>

              <!-- 참고 이미지 (화면 시안/스크린샷) -->
              <div v-if="selectedNodeData.kind !== 'start' && selectedNodeData.kind !== 'end'" class="mt-3">
                <span class="block text-xs font-medium text-slate-600 dark:text-slate-300">{{ $t('testflow.panel.imageLabel') }}</span>
                <div v-if="selectedNodeData.image" class="relative mt-1">
                  <img
                    :src="selectedNodeData.image"
                    :alt="selectedNodeData.label"
                    class="h-28 w-full cursor-zoom-in rounded-md border border-slate-200 object-cover dark:border-slate-700"
                    @click="lightboxSrc = selectedNodeData.image"
                  />
                  <button
                    type="button"
                    :title="$t('testflow.panel.removeImage')"
                    :aria-label="$t('testflow.panel.removeImage')"
                    class="absolute right-1.5 top-1.5 rounded-full bg-black/60 p-1 text-white hover:bg-black/80"
                    @click="patchSelectedNode({ image: '' })"
                  >
                    <X class="h-3 w-3" />
                  </button>
                </div>
                <button
                  v-else
                  type="button"
                  :disabled="uploadingImage"
                  class="mt-1 flex w-full items-center justify-center gap-1.5 rounded-md border border-dashed border-slate-300 px-3 py-3 text-xs font-medium text-slate-500 hover:border-emerald-400 hover:text-emerald-600 disabled:opacity-60 dark:border-slate-700 dark:text-slate-400 dark:hover:border-emerald-500/60 dark:hover:text-emerald-400"
                  @click="nodeImageInput?.click()"
                >
                  <LoaderCircle v-if="uploadingImage" class="h-3.5 w-3.5 animate-spin" />
                  <ImagePlus v-else class="h-3.5 w-3.5" />
                  {{ uploadingImage ? $t('common.state.processing') : $t('testflow.panel.uploadImage') }}
                </button>
                <input ref="nodeImageInput" type="file" accept="image/*" class="hidden" @change="onNodeImagePick" />
              </div>
              <button
                v-if="selectedNodeData.kind !== 'start' && selectedNodeData.kind !== 'end'"
                type="button"
                class="mt-4 flex w-full items-center justify-center gap-1.5 rounded-md border border-red-200 px-3 py-1.5 text-xs font-medium text-red-600 hover:bg-red-50 dark:border-red-500/40 dark:text-red-400 dark:hover:bg-red-500/10"
                @click="removeSelectedNode"
              >
                <Trash2 class="h-3.5 w-3.5" />{{ $t('testflow.panel.deleteNode') }}
              </button>
            </template>

            <template v-else-if="selectedEdge">
              <h3 class="mb-1 text-xs font-semibold text-slate-700 dark:text-slate-200">{{ $t('testflow.panel.edgeTitle') }}</h3>
              <p class="mb-3 truncate text-[11px] text-slate-400 dark:text-slate-500" :title="edgeContext">{{ edgeContext }}</p>
              <label class="block">
                <span class="block text-xs font-medium text-slate-600 dark:text-slate-300">{{ $t('testflow.panel.edgeLabelLabel') }}</span>
                <input
                  :value="selectedEdgeLabel"
                  type="text"
                  maxlength="100"
                  :placeholder="$t('testflow.panel.edgeLabelPlaceholder')"
                  class="mt-1 w-full rounded-md border border-slate-300 px-3 py-1.5 text-xs focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100 dark:placeholder-slate-500"
                  @input="setSelectedEdgeLabel(($event.target as HTMLInputElement).value)"
                />
              </label>
              <button
                type="button"
                class="mt-4 flex w-full items-center justify-center gap-1.5 rounded-md border border-red-200 px-3 py-1.5 text-xs font-medium text-red-600 hover:bg-red-50 dark:border-red-500/40 dark:text-red-400 dark:hover:bg-red-500/10"
                @click="removeSelectedEdge"
              >
                <Trash2 class="h-3.5 w-3.5" />{{ $t('testflow.panel.deleteEdge') }}
              </button>
            </template>

            <p v-else class="py-6 text-center text-xs text-slate-400 dark:text-slate-500">{{ $t('testflow.panel.emptyHint') }}</p>
          </div>
        </aside>
      </div>
    </template>

    <DeleteConfirmModal
      :open="deleteOpen"
      :title="$t('testflow.deleteConfirm.title')"
      :message="$t('testflow.deleteConfirm.message')"
      @confirm="confirmDeleteFlow"
      @cancel="deleteOpen = false"
    />

    <TestFlowPathModal
      v-if="selectedFlowId != null && pathGraph"
      :open="pathModalOpen"
      :project-id="projectId"
      :flow-id="selectedFlowId"
      :flow-name="savedName"
      :update-id="linkedUpdateId"
      :updates="updates"
      :graph="pathGraph"
      :suites="suites"
      @close="pathModalOpen = false"
      @created="onCasesCreated"
    />

    <!-- 노드 이미지 확대 보기 -->
    <ImageLightbox :src="lightboxSrc" @close="lightboxSrc = null" />
  </div>
</template>

<style>
/* VueFlow 엣지/핸들/컨트롤 색 보정 (라이트/다크) — 딥 셀렉터가 필요해 전역 스타일 사용 */
.vue-flow__edge-path {
  stroke: #94a3b8;
}
.vue-flow__edge.selected .vue-flow__edge-path {
  stroke: #10b981;
}
.vue-flow__edge-textbg {
  fill: #ffffff;
}
.vue-flow__edge-text {
  fill: #475569;
  font-size: 10px;
}
.vue-flow__handle {
  width: 8px;
  height: 8px;
  background: #94a3b8;
  border-color: #ffffff;
}
.dark .vue-flow__edge-path {
  stroke: #475569;
}
.dark .vue-flow__edge.selected .vue-flow__edge-path {
  stroke: #34d399;
}
.dark .vue-flow__edge-textbg {
  fill: #0f172a;
}
.dark .vue-flow__edge-text {
  fill: #94a3b8;
}
.dark .vue-flow__handle {
  background: #64748b;
  border-color: #0f172a;
}
.dark .vue-flow__controls {
  box-shadow: none;
}
.dark .vue-flow__controls-button {
  background: #1e293b;
  border-bottom: 1px solid #334155;
}
.dark .vue-flow__controls-button svg {
  fill: #cbd5e1;
}
.dark .vue-flow__controls-button:hover {
  background: #334155;
}
</style>
