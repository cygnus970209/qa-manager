package com.qamanager.testing;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 테스트 스위트/케이스/플로우 API. 모든 멤버 사용 가능(협업 기능). */
@RestController
public class TestCaseController {

    private final TestingService service;

    public TestCaseController(TestingService service) {
        this.service = service;
    }

    /* ── Suites ── */

    @GetMapping("/api/projects/{projectId}/test-suites")
    public List<TestingDto.SuiteResponse> listSuites(@PathVariable Long projectId) {
        return service.listSuites(projectId);
    }

    @PostMapping("/api/projects/{projectId}/test-suites")
    public TestingDto.SuiteResponse createSuite(@PathVariable Long projectId,
                                                @RequestBody @Valid TestingDto.SuiteCreateRequest req) {
        return service.createSuite(projectId, req);
    }

    @PatchMapping("/api/test-suites/{id}")
    public TestingDto.SuiteResponse updateSuite(@PathVariable Long id,
                                                @RequestBody @Valid TestingDto.SuiteUpdateRequest req) {
        return service.updateSuite(id, req);
    }

    @DeleteMapping("/api/test-suites/{id}")
    public ResponseEntity<Void> deleteSuite(@PathVariable Long id) {
        service.deleteSuite(id);
        return ResponseEntity.noContent().build();
    }

    /* ── Cases ── */

    @GetMapping("/api/projects/{projectId}/test-cases")
    public List<TestingDto.CaseResponse> listCases(@PathVariable Long projectId) {
        return service.listCases(projectId);
    }

    @PostMapping("/api/projects/{projectId}/test-cases")
    public TestingDto.CaseResponse createCase(@PathVariable Long projectId,
                                              @RequestBody @Valid TestingDto.CaseCreateRequest req) {
        return service.createCase(projectId, req);
    }

    @PostMapping("/api/projects/{projectId}/test-cases/bulk")
    public List<TestingDto.CaseResponse> bulkCreateCases(@PathVariable Long projectId,
                                                         @RequestBody @Valid TestingDto.CaseBulkCreateRequest req) {
        return service.bulkCreateCases(projectId, req);
    }

    @PatchMapping("/api/test-cases/{id}")
    public TestingDto.CaseResponse updateCase(@PathVariable Long id,
                                              @RequestBody @Valid TestingDto.CaseUpdateRequest req) {
        return service.updateCase(id, req);
    }

    @DeleteMapping("/api/test-cases/{id}")
    public ResponseEntity<Void> deleteCase(@PathVariable Long id) {
        service.deleteCase(id);
        return ResponseEntity.noContent().build();
    }

    /* ── Flows ── */

    @GetMapping("/api/projects/{projectId}/test-flows")
    public List<TestingDto.FlowSummaryResponse> listFlows(@PathVariable Long projectId) {
        return service.listFlows(projectId);
    }

    @PostMapping("/api/projects/{projectId}/test-flows")
    public TestingDto.FlowResponse createFlow(@PathVariable Long projectId,
                                              @RequestBody @Valid TestingDto.FlowCreateRequest req) {
        return service.createFlow(projectId, req);
    }

    @GetMapping("/api/test-flows/{id}")
    public TestingDto.FlowResponse getFlow(@PathVariable Long id) {
        return service.getFlow(id);
    }

    @PatchMapping("/api/test-flows/{id}")
    public TestingDto.FlowResponse updateFlow(@PathVariable Long id,
                                              @RequestBody @Valid TestingDto.FlowUpdateRequest req) {
        return service.updateFlow(id, req);
    }

    @DeleteMapping("/api/test-flows/{id}")
    public ResponseEntity<Void> deleteFlow(@PathVariable Long id) {
        service.deleteFlow(id);
        return ResponseEntity.noContent().build();
    }
}
