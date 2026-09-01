import type {
  FlowGraph,
  TestPlatform,
  TestCase,
  TestCaseCreateRequest,
  TestCaseUpdateRequest,
  TestFlow,
  TestFlowSummary,
  TestRun,
  TestRunCase,
  TestRunCaseResult,
  TestRunDetail,
  TestSuite,
} from '~/types/api'

/** 테스트 케이스 관리(스위트/케이스/플로우/런) API 클라이언트. */
export function useTesting() {
  const api = useApi()
  return {
    /* ── Suites ── */
    listSuites: (projectId: number) => api<TestSuite[]>(`/api/projects/${projectId}/test-suites`),
    createSuite: (projectId: number, name: string) =>
      api<TestSuite>(`/api/projects/${projectId}/test-suites`, { method: 'POST', body: { name } }),
    updateSuite: (id: number, body: { name?: string; sortOrder?: number }) =>
      api<TestSuite>(`/api/test-suites/${id}`, { method: 'PATCH', body }),
    removeSuite: (id: number) => api(`/api/test-suites/${id}`, { method: 'DELETE' }),

    /* ── Cases ── */
    listCases: (projectId: number) => api<TestCase[]>(`/api/projects/${projectId}/test-cases`),
    createCase: (projectId: number, body: TestCaseCreateRequest) =>
      api<TestCase>(`/api/projects/${projectId}/test-cases`, { method: 'POST', body }),
    bulkCreateCases: (projectId: number, body: { suiteId?: number | null; flowId?: number | null; cases: TestCaseCreateRequest[] }) =>
      api<TestCase[]>(`/api/projects/${projectId}/test-cases/bulk`, { method: 'POST', body }),
    updateCase: (id: number, body: TestCaseUpdateRequest) =>
      api<TestCase>(`/api/test-cases/${id}`, { method: 'PATCH', body }),
    removeCase: (id: number) => api(`/api/test-cases/${id}`, { method: 'DELETE' }),

    /* ── Flows ── */
    listFlows: (projectId: number) => api<TestFlowSummary[]>(`/api/projects/${projectId}/test-flows`),
    getFlow: (id: number) => api<TestFlow>(`/api/test-flows/${id}`),
    createFlow: (projectId: number, body: { name: string; updateId?: number | null }) =>
      api<TestFlow>(`/api/projects/${projectId}/test-flows`, { method: 'POST', body }),
    updateFlow: (id: number, body: { name?: string; updateId?: number; graph?: FlowGraph }) =>
      api<TestFlow>(`/api/test-flows/${id}`, { method: 'PATCH', body }),
    removeFlow: (id: number) => api(`/api/test-flows/${id}`, { method: 'DELETE' }),

    /* ── Runs ── */
    listRunsByUpdate: (updateId: number) => api<TestRun[]>(`/api/updates/${updateId}/test-runs`),
    listRunsByProject: (projectId: number) => api<TestRun[]>(`/api/projects/${projectId}/test-runs`),
    createRun: (updateId: number, body: { name: string; caseIds: number[]; platforms?: TestPlatform[] }) =>
      api<TestRunDetail>(`/api/updates/${updateId}/test-runs`, { method: 'POST', body }),
    getRun: (id: number) => api<TestRunDetail>(`/api/test-runs/${id}`),
    updateRun: (id: number, body: { closed: boolean }) =>
      api<TestRun>(`/api/test-runs/${id}`, { method: 'PATCH', body }),
    removeRun: (id: number) => api(`/api/test-runs/${id}`, { method: 'DELETE' }),
    updateRunCase: (id: number, body: { result?: TestRunCaseResult; note?: string; qaItemId?: number }) =>
      api<TestRunCase>(`/api/test-run-cases/${id}`, { method: 'PATCH', body }),
  }
}
