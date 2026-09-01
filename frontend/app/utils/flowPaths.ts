import type { FlowEdge, FlowGraph, FlowNode, TestStep } from '~/types/api'

/**
 * 워크플로우 그래프에서 시작→종료 경로를 열거해 시나리오 테스트 케이스 초안을 만든다.
 * - 단순 경로(같은 노드 재방문 없음)만 열거 — 사이클(뒤로 가기 등)은 1회 순회로 제한된다.
 * - 경로 폭발 방지: MAX_PATHS 에서 열거를 중단하고 truncated=true 로 알린다.
 * - start 노드가 없으면 "들어오는 엣지가 없는 노드", end 가 없으면 "나가는 엣지가 없는 노드"를 종점으로 삼는다.
 */

export interface FlowPath {
  /** 경로상 노드 id 순열 — 케이스의 flow 링크 키로도 사용 */
  nodeIds: string[]
  nodes: FlowNode[]
  /** 노드 사이 엣지(분기 라벨 포함) */
  edges: (FlowEdge | null)[]
  /** 자동 생성 제목: 분기 선택 요약 */
  title: string
  steps: TestStep[]
}

export interface FlowPathResult {
  paths: FlowPath[]
  truncated: boolean
}

export const MAX_PATHS = 100

export function enumerateFlowPaths(graph: FlowGraph): FlowPathResult {
  const nodes = new Map(graph.nodes.map((n) => [n.id, n]))
  const outgoing = new Map<string, FlowEdge[]>()
  const incoming = new Map<string, number>()
  for (const e of graph.edges) {
    if (!nodes.has(e.source) || !nodes.has(e.target)) continue
    const arr = outgoing.get(e.source) ?? []
    arr.push(e)
    outgoing.set(e.source, arr)
    incoming.set(e.target, (incoming.get(e.target) ?? 0) + 1)
  }

  const explicitStarts = graph.nodes.filter((n) => n.type === 'start')
  const starts = explicitStarts.length > 0
    ? explicitStarts
    : graph.nodes.filter((n) => !incoming.has(n.id) && (outgoing.get(n.id)?.length ?? 0) > 0)

  const isTerminal = (id: string): boolean => {
    const node = nodes.get(id)
    if (node?.type === 'end') return true
    return (outgoing.get(id)?.length ?? 0) === 0
  }

  const paths: FlowPath[] = []
  let truncated = false

  function dfs(nodeId: string, visited: Set<string>, pathNodes: string[], pathEdges: (FlowEdge | null)[]) {
    if (truncated) return
    if (isTerminal(nodeId)) {
      if (paths.length >= MAX_PATHS) {
        truncated = true
        return
      }
      paths.push(buildPath(pathNodes, pathEdges, nodes))
      return
    }
    for (const edge of outgoing.get(nodeId) ?? []) {
      if (visited.has(edge.target)) continue // 사이클 1회 순회 제한
      visited.add(edge.target)
      dfs(edge.target, visited, [...pathNodes, edge.target], [...pathEdges, edge])
      visited.delete(edge.target)
    }
  }

  for (const start of starts) {
    dfs(start.id, new Set([start.id]), [start.id], [])
  }
  return { paths, truncated }
}

function buildPath(nodeIds: string[], edges: (FlowEdge | null)[], nodeMap: Map<string, FlowNode>): FlowPath {
  const pathNodes = nodeIds.map((id) => nodeMap.get(id)!).filter(Boolean)

  // 제목: 화면/행동 노드 라벨 나열 + 분기 선택은 "라벨(선택)" 로 표기. start/end 는 생략.
  const parts: string[] = []
  for (let i = 0; i < pathNodes.length; i++) {
    const n = pathNodes[i]!
    if (n.type === 'start' || n.type === 'end') continue
    if (n.type === 'decision') {
      const chosen = edges[i]?.label // decision 노드에서 나가는 엣지의 라벨
      parts.push(chosen ? `${n.label}(${chosen})` : n.label)
    } else {
      parts.push(n.label)
    }
  }
  const title = parts.join(' → ')

  // 스텝: start/end 제외한 노드 순서. action = 라벨(분기는 선택 라벨 포함), expected = 노드의 확인 포인트.
  const steps: TestStep[] = []
  for (let i = 0; i < pathNodes.length; i++) {
    const n = pathNodes[i]!
    if (n.type === 'start' || n.type === 'end') continue
    let action = n.label
    if (n.type === 'decision') {
      const chosen = edges[i]?.label
      if (chosen) action = `${n.label} — ${chosen}`
    }
    const step: TestStep = { action, expected: n.expected ?? '' }
    if (n.image) step.image = n.image
    steps.push(step)
  }

  return { nodeIds, nodes: pathNodes, edges, title, steps }
}
