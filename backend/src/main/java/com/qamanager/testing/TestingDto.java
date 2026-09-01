package com.qamanager.testing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * testing 도메인 DTO.
 * steps/graph 는 프론트가 스키마를 소유하는 JSON — 백엔드는 JsonNode 로 통과시키고 문자열로 보관한다.
 */
public class TestingDto {

    /* ─────────────── Suite ─────────────── */

    public record SuiteResponse(Long id, Long projectId, String name, int sortOrder) {
        public static SuiteResponse from(TestSuite s) {
            return new SuiteResponse(s.getId(), s.getProjectId(), s.getName(), s.getSortOrder());
        }
    }

    public record SuiteCreateRequest(@NotBlank @Size(max = 100) String name) {}

    public record SuiteUpdateRequest(@Size(max = 100) String name, Integer sortOrder) {}

    /* ─────────────── Case ─────────────── */

    public record CaseResponse(
        Long id, Long projectId, Long suiteId,
        String title, String precondition, JsonNode steps,
        String priority, String origin, Long flowId, boolean flowStale,
        LocalDateTime createdAt, LocalDateTime updatedAt
    ) {}

    public record CaseCreateRequest(
        Long suiteId,
        @NotBlank @Size(max = 200) String title,
        String precondition,
        @NotNull JsonNode steps,
        String priority
    ) {}

    public record CaseUpdateRequest(
        Long suiteId,
        @Size(max = 200) String title,
        String precondition,
        JsonNode steps,
        String priority
    ) {}

    /** 플로우 경로에서 생성한 케이스 일괄 등록. */
    public record CaseBulkCreateRequest(
        Long suiteId,
        Long flowId,
        @NotEmpty List<CaseCreateRequest> cases
    ) {}

    /* ─────────────── Flow ─────────────── */

    public record FlowSummaryResponse(Long id, Long projectId, Long updateId, String name, LocalDateTime updatedAt) {
        public static FlowSummaryResponse from(TestFlow f) {
            return new FlowSummaryResponse(f.getId(), f.getProjectId(), f.getUpdateId(), f.getName(), f.getUpdatedAt());
        }
    }

    public record FlowResponse(Long id, Long projectId, Long updateId, String name, JsonNode graph, LocalDateTime updatedAt) {}

    public record FlowCreateRequest(@NotBlank @Size(max = 150) String name, Long updateId) {}

    public record FlowUpdateRequest(@Size(max = 150) String name, Long updateId, JsonNode graph) {}

    /* ─────────────── Run ─────────────── */

    public record RunStats(long total, long pass, long fail, long blocked, long skip, long pending) {}

    public record RunResponse(Long id, Long updateId, String name, LocalDateTime closedAt,
                              LocalDateTime createdAt, RunStats stats) {}

    public record RunCreateRequest(
        @NotBlank @Size(max = 150) String name,
        @NotEmpty List<Long> caseIds,
        /** 실행 플랫폼(PC/ANDROID/IOS) 다중 선택. 비어 있으면 플랫폼 구분 없는 공통 1회. */
        List<String> platforms
    ) {}

    public record RunUpdateRequest(@NotNull Boolean closed) {}

    public record RunCaseResponse(
        Long id, Long runId, Long caseId, String platform, int sortOrder,
        String title, String precondition, JsonNode steps, String priority,
        String result, String note, Long qaItemId, LocalDateTime executedAt
    ) {}

    public record RunDetailResponse(RunResponse run, List<RunCaseResponse> cases) {}

    public record RunCaseUpdateRequest(String result, String note, Long qaItemId) {}
}
