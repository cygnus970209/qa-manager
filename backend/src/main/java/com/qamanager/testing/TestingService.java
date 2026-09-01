package com.qamanager.testing;

import com.qamanager.common.ApiException;
import com.qamanager.project.ProjectRepository;
import com.qamanager.projectupdate.ProjectUpdateRepository;
import com.qamanager.qa.shared.QaPriority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class TestingService {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final String EMPTY_GRAPH = "{\"nodes\":[],\"edges\":[]}";

    private final TestSuiteRepository suiteRepository;
    private final TestCaseRepository caseRepository;
    private final TestFlowRepository flowRepository;
    private final TestRunRepository runRepository;
    private final TestRunCaseRepository runCaseRepository;
    private final ProjectRepository projectRepository;
    private final ProjectUpdateRepository updateRepository;

    public TestingService(TestSuiteRepository suiteRepository,
                          TestCaseRepository caseRepository,
                          TestFlowRepository flowRepository,
                          TestRunRepository runRepository,
                          TestRunCaseRepository runCaseRepository,
                          ProjectRepository projectRepository,
                          ProjectUpdateRepository updateRepository) {
        this.suiteRepository = suiteRepository;
        this.caseRepository = caseRepository;
        this.flowRepository = flowRepository;
        this.runRepository = runRepository;
        this.runCaseRepository = runCaseRepository;
        this.projectRepository = projectRepository;
        this.updateRepository = updateRepository;
    }

    /* ─────────────── Suite ─────────────── */

    public List<TestingDto.SuiteResponse> listSuites(Long projectId) {
        return suiteRepository.findByProjectIdOrderBySortOrderAscIdAsc(projectId)
            .stream().map(TestingDto.SuiteResponse::from).toList();
    }

    @Transactional
    public TestingDto.SuiteResponse createSuite(Long projectId, TestingDto.SuiteCreateRequest req) {
        requireProject(projectId);
        int order = suiteRepository.findByProjectIdOrderBySortOrderAscIdAsc(projectId).size();
        return TestingDto.SuiteResponse.from(suiteRepository.save(new TestSuite(projectId, req.name().trim(), order)));
    }

    @Transactional
    public TestingDto.SuiteResponse updateSuite(Long id, TestingDto.SuiteUpdateRequest req) {
        TestSuite s = suiteRepository.findById(id)
            .orElseThrow(() -> ApiException.notFound("테스트 스위트를 찾을 수 없습니다."));
        s.update(req.name(), req.sortOrder());
        return TestingDto.SuiteResponse.from(s);
    }

    @Transactional
    public void deleteSuite(Long id) {
        // 소속 케이스는 DB FK(ON DELETE SET NULL)로 미분류 처리된다.
        suiteRepository.deleteById(id);
    }

    /* ─────────────── Case ─────────────── */

    public List<TestingDto.CaseResponse> listCases(Long projectId) {
        return caseRepository.findByProjectIdOrderByIdAsc(projectId).stream().map(this::toCaseResponse).toList();
    }

    @Transactional
    public TestingDto.CaseResponse createCase(Long projectId, TestingDto.CaseCreateRequest req) {
        requireProject(projectId);
        TestCase c = buildCase(projectId, req, TestCase.Origin.MANUAL, null);
        return toCaseResponse(caseRepository.save(c));
    }

    /** 플로우 경로에서 선택한 케이스 일괄 생성. */
    @Transactional
    public List<TestingDto.CaseResponse> bulkCreateCases(Long projectId, TestingDto.CaseBulkCreateRequest req) {
        requireProject(projectId);
        TestCase.Origin origin = req.flowId() != null ? TestCase.Origin.FLOW : TestCase.Origin.MANUAL;
        List<TestingDto.CaseResponse> out = new ArrayList<>();
        for (TestingDto.CaseCreateRequest cr : req.cases()) {
            TestingDto.CaseCreateRequest merged = new TestingDto.CaseCreateRequest(
                cr.suiteId() != null ? cr.suiteId() : req.suiteId(),
                cr.title(), cr.precondition(), cr.steps(), cr.priority());
            out.add(toCaseResponse(caseRepository.save(buildCase(projectId, merged, origin, req.flowId()))));
        }
        return out;
    }

    @Transactional
    public TestingDto.CaseResponse updateCase(Long id, TestingDto.CaseUpdateRequest req) {
        TestCase c = caseRepository.findById(id)
            .orElseThrow(() -> ApiException.notFound("테스트 케이스를 찾을 수 없습니다."));
        String stepsJson = req.steps() != null ? writeSteps(req.steps()) : null;
        String priority = req.priority() != null ? normalizePriority(req.priority()) : null;
        c.update(req.suiteId(), req.title(), req.precondition(), stepsJson, priority);
        // 사용자가 직접 손본 케이스는 stale 표시를 해제한다 (검토 완료로 간주).
        if (stepsJson != null || req.title() != null) c.markFlowStale(false);
        return toCaseResponse(c);
    }

    @Transactional
    public void deleteCase(Long id) {
        caseRepository.deleteById(id);
    }

    /* ─────────────── Flow ─────────────── */

    public List<TestingDto.FlowSummaryResponse> listFlows(Long projectId) {
        return flowRepository.findByProjectIdOrderByIdDesc(projectId)
            .stream().map(TestingDto.FlowSummaryResponse::from).toList();
    }

    public TestingDto.FlowResponse getFlow(Long id) {
        return toFlowResponse(findFlow(id));
    }

    @Transactional
    public TestingDto.FlowResponse createFlow(Long projectId, TestingDto.FlowCreateRequest req) {
        requireProject(projectId);
        if (req.updateId() != null) requireUpdate(req.updateId());
        TestFlow f = flowRepository.save(new TestFlow(projectId, req.updateId(), req.name().trim(), EMPTY_GRAPH));
        return toFlowResponse(f);
    }

    @Transactional
    public TestingDto.FlowResponse updateFlow(Long id, TestingDto.FlowUpdateRequest req) {
        TestFlow f = findFlow(id);
        if (req.updateId() != null && req.updateId() != 0) requireUpdate(req.updateId());
        String graphJson = req.graph() != null ? MAPPER.writeValueAsString(req.graph()) : null;
        boolean graphChanged = f.update(req.name(), req.updateId(), graphJson);
        if (graphChanged) {
            // 이 플로우에서 생성된 케이스에 "원본 그래프 변경" 표시 — 자동 재생성은 하지 않는다.
            caseRepository.findByFlowId(f.getId()).forEach(c -> c.markFlowStale(true));
        }
        return toFlowResponse(f);
    }

    @Transactional
    public void deleteFlow(Long id) {
        // 생성된 케이스는 FK(ON DELETE SET NULL)로 링크만 끊기고 보존된다.
        flowRepository.deleteById(id);
    }

    /* ─────────────── Run ─────────────── */

    public List<TestingDto.RunResponse> listRunsByUpdate(Long updateId) {
        return withStats(runRepository.findByUpdateIdOrderByIdDesc(updateId));
    }

    public List<TestingDto.RunResponse> listRunsByProject(Long projectId) {
        return withStats(runRepository.findByProjectId(projectId));
    }

    @Transactional
    public TestingDto.RunDetailResponse createRun(Long updateId, TestingDto.RunCreateRequest req) {
        requireUpdate(updateId);
        List<TestCase> cases = caseRepository.findByIdIn(req.caseIds());
        if (cases.isEmpty()) throw ApiException.badRequest("선택된 테스트 케이스가 없습니다.");
        Map<Long, TestCase> byId = new HashMap<>();
        cases.forEach(c -> byId.put(c.getId(), c));

        // 플랫폼 다중 선택 시 케이스 × 플랫폼으로 실행 항목 확장. 비어 있으면 공통 1회(null).
        List<String> platforms = normalizePlatforms(req.platforms());

        TestRun run = runRepository.save(new TestRun(updateId, req.name().trim()));
        int order = 0;
        List<TestRunCase> items = new ArrayList<>();
        for (Long caseId : req.caseIds()) {
            TestCase src = byId.get(caseId);
            if (src == null) continue; // 사이 삭제된 케이스는 조용히 제외
            for (String platform : platforms) {
                items.add(new TestRunCase(run.getId(), src, order++, platform));
            }
        }
        runCaseRepository.saveAll(items);
        return getRunDetail(run.getId());
    }

    public TestingDto.RunDetailResponse getRunDetail(Long runId) {
        TestRun run = findRun(runId);
        List<TestRunCase> items = runCaseRepository.findByRunIdOrderBySortOrderAscIdAsc(runId);
        TestingDto.RunStats stats = statsOf(items);
        List<TestingDto.RunCaseResponse> cases = items.stream().map(this::toRunCaseResponse).toList();
        return new TestingDto.RunDetailResponse(toRunResponse(run, stats), cases);
    }

    @Transactional
    public TestingDto.RunResponse updateRun(Long runId, TestingDto.RunUpdateRequest req) {
        TestRun run = findRun(runId);
        run.setClosed(req.closed());
        return toRunResponse(run, statsOf(runCaseRepository.findByRunIdOrderBySortOrderAscIdAsc(runId)));
    }

    @Transactional
    public void deleteRun(Long runId) {
        runRepository.deleteById(runId); // 실행 항목은 DB CASCADE 로 함께 삭제
    }

    @Transactional
    public TestingDto.RunCaseResponse updateRunCase(Long id, TestingDto.RunCaseUpdateRequest req) {
        TestRunCase c = runCaseRepository.findById(id)
            .orElseThrow(() -> ApiException.notFound("실행 항목을 찾을 수 없습니다."));
        if (req.result() != null) validateResult(req.result());
        c.recordResult(req.result(), req.note(), req.qaItemId());
        return toRunCaseResponse(c);
    }

    /* ─────────────── 내부 헬퍼 ─────────────── */

    private TestCase buildCase(Long projectId, TestingDto.CaseCreateRequest req, TestCase.Origin origin, Long flowId) {
        if (req.suiteId() != null) {
            TestSuite s = suiteRepository.findById(req.suiteId())
                .orElseThrow(() -> ApiException.badRequest("존재하지 않는 스위트입니다."));
            if (!s.getProjectId().equals(projectId)) throw ApiException.badRequest("다른 프로젝트의 스위트입니다.");
        }
        return new TestCase(projectId, req.suiteId(), req.title().trim(),
            req.precondition() != null && !req.precondition().isBlank() ? req.precondition() : null,
            writeSteps(req.steps()), normalizePriority(req.priority()), origin, flowId);
    }

    private String writeSteps(JsonNode steps) {
        if (steps == null || !steps.isArray()) throw ApiException.badRequest("steps 는 배열이어야 합니다.");
        return MAPPER.writeValueAsString(steps);
    }

    private String normalizePriority(String priority) {
        if (priority == null || priority.isBlank()) return QaPriority.MEDIUM.getCode();
        try {
            return QaPriority.from(priority).getCode();
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("올바르지 않은 우선순위입니다: " + priority);
        }
    }

    /** 플랫폼 목록 정규화: 검증 + 중복 제거. 비어 있으면 [null](공통 1회). */
    private List<String> normalizePlatforms(List<String> platforms) {
        if (platforms == null || platforms.isEmpty()) {
            List<String> single = new ArrayList<>();
            single.add(null);
            return single;
        }
        List<String> out = new ArrayList<>();
        for (String p : platforms) {
            try {
                String name = TestRunCase.Platform.valueOf(p).name();
                if (!out.contains(name)) out.add(name);
            } catch (IllegalArgumentException | NullPointerException e) {
                throw ApiException.badRequest("올바르지 않은 플랫폼입니다: " + p);
            }
        }
        return out;
    }

    private void validateResult(String result) {
        try {
            TestRunCase.Result.valueOf(result);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("올바르지 않은 결과 값입니다: " + result);
        }
    }

    private void requireProject(Long projectId) {
        if (!projectRepository.existsById(projectId)) throw ApiException.notFound("프로젝트를 찾을 수 없습니다.");
    }

    private void requireUpdate(Long updateId) {
        if (!updateRepository.existsById(updateId)) throw ApiException.notFound("업데이트를 찾을 수 없습니다.");
    }

    private TestFlow findFlow(Long id) {
        return flowRepository.findById(id).orElseThrow(() -> ApiException.notFound("플로우를 찾을 수 없습니다."));
    }

    private TestRun findRun(Long id) {
        return runRepository.findById(id).orElseThrow(() -> ApiException.notFound("테스트 런을 찾을 수 없습니다."));
    }

    private List<TestingDto.RunResponse> withStats(List<TestRun> runs) {
        if (runs.isEmpty()) return List.of();
        List<Long> ids = runs.stream().map(TestRun::getId).toList();
        Map<Long, Map<String, Long>> counts = new HashMap<>();
        for (Object[] row : runCaseRepository.countResultsByRunIds(ids)) {
            counts.computeIfAbsent((Long) row[0], k -> new HashMap<>()).put((String) row[1], (Long) row[2]);
        }
        return runs.stream().map(r -> {
            Map<String, Long> c = counts.getOrDefault(r.getId(), Map.of());
            long pass = c.getOrDefault("PASS", 0L);
            long fail = c.getOrDefault("FAIL", 0L);
            long blocked = c.getOrDefault("BLOCKED", 0L);
            long skip = c.getOrDefault("SKIP", 0L);
            long pending = c.getOrDefault("PENDING", 0L);
            return toRunResponse(r, new TestingDto.RunStats(pass + fail + blocked + skip + pending, pass, fail, blocked, skip, pending));
        }).toList();
    }

    private TestingDto.RunStats statsOf(List<TestRunCase> items) {
        long pass = 0, fail = 0, blocked = 0, skip = 0, pending = 0;
        for (TestRunCase c : items) {
            switch (TestRunCase.Result.valueOf(c.getResult())) {
                case PASS -> pass++;
                case FAIL -> fail++;
                case BLOCKED -> blocked++;
                case SKIP -> skip++;
                case PENDING -> pending++;
            }
        }
        return new TestingDto.RunStats(items.size(), pass, fail, blocked, skip, pending);
    }

    private TestingDto.RunResponse toRunResponse(TestRun r, TestingDto.RunStats stats) {
        return new TestingDto.RunResponse(r.getId(), r.getUpdateId(), r.getName(), r.getClosedAt(), r.getCreatedAt(), stats);
    }

    private TestingDto.RunCaseResponse toRunCaseResponse(TestRunCase c) {
        return new TestingDto.RunCaseResponse(c.getId(), c.getRunId(), c.getCaseId(), c.getPlatform(), c.getSortOrder(),
            c.getTitle(), c.getPrecondition(), MAPPER.readTree(c.getStepsJson()), c.getPriority(),
            c.getResult(), c.getNote(), c.getQaItemId(), c.getExecutedAt());
    }

    private TestingDto.CaseResponse toCaseResponse(TestCase c) {
        return new TestingDto.CaseResponse(c.getId(), c.getProjectId(), c.getSuiteId(), c.getTitle(),
            c.getPrecondition(), MAPPER.readTree(c.getStepsJson()), c.getPriority(), c.getOrigin(),
            c.getFlowId(), c.isFlowStale(), c.getCreatedAt(), c.getUpdatedAt());
    }

    private TestingDto.FlowResponse toFlowResponse(TestFlow f) {
        return new TestingDto.FlowResponse(f.getId(), f.getProjectId(), f.getUpdateId(), f.getName(),
            MAPPER.readTree(f.getGraphJson()), f.getUpdatedAt());
    }
}
