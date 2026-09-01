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

/** 테스트 런(실행) API. */
@RestController
public class TestRunController {

    private final TestingService service;

    public TestRunController(TestingService service) {
        this.service = service;
    }

    @GetMapping("/api/updates/{updateId}/test-runs")
    public List<TestingDto.RunResponse> listByUpdate(@PathVariable Long updateId) {
        return service.listRunsByUpdate(updateId);
    }

    /** 프로젝트 상세 아코디언 요약용 — 프로젝트 내 전체 런. */
    @GetMapping("/api/projects/{projectId}/test-runs")
    public List<TestingDto.RunResponse> listByProject(@PathVariable Long projectId) {
        return service.listRunsByProject(projectId);
    }

    @PostMapping("/api/updates/{updateId}/test-runs")
    public TestingDto.RunDetailResponse create(@PathVariable Long updateId,
                                               @RequestBody @Valid TestingDto.RunCreateRequest req) {
        return service.createRun(updateId, req);
    }

    @GetMapping("/api/test-runs/{id}")
    public TestingDto.RunDetailResponse detail(@PathVariable Long id) {
        return service.getRunDetail(id);
    }

    @PatchMapping("/api/test-runs/{id}")
    public TestingDto.RunResponse update(@PathVariable Long id,
                                         @RequestBody @Valid TestingDto.RunUpdateRequest req) {
        return service.updateRun(id, req);
    }

    @DeleteMapping("/api/test-runs/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deleteRun(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/api/test-run-cases/{id}")
    public TestingDto.RunCaseResponse updateRunCase(@PathVariable Long id,
                                                    @RequestBody @Valid TestingDto.RunCaseUpdateRequest req) {
        return service.updateRunCase(id, req);
    }
}
