package com.qamanager.qa.item;

import com.qamanager.integration.github.QaGithubIssue;
import com.qamanager.member.TeamMember;
import com.qamanager.qa.shared.QaPriority;
import com.qamanager.qa.shared.QaStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public class QaDto {

    public record AssigneeSummary(Long id, String name, String avatarUrl) {
        public static AssigneeSummary of(TeamMember m) {
            return m == null ? null : new AssigneeSummary(m.getId(), m.getName(), m.getAvatarUrl());
        }
    }

    /** 연결된 GitHub 이슈 요약 (미연결이면 응답에서 생략). */
    public record GithubIssue(Integer issueNumber, String issueUrl, String state,
                              String repoOwner, String repoName) {
        public static GithubIssue from(QaGithubIssue m) {
            return m == null ? null : new GithubIssue(
                m.getIssueNumber(), m.getIssueUrl(), m.getState(), m.getRepoOwner(), m.getRepoName());
        }
    }

    public record Response(
        Long id,
        Long updateId,
        String title,
        String description,
        String category,
        String status,
        AssigneeSummary tester,
        AssigneeSummary assignee1,
        AssigneeSummary assignee2,
        String priority,
        List<String> images,
        GithubIssue githubIssue,
        String createdAt,
        String updatedAt
    ) {
        public static Response from(QaItem q) {
            return from(q, null);
        }

        public static Response from(QaItem q, GithubIssue githubIssue) {
            return new Response(
                q.getId(),
                q.getProjectUpdate().getId(),
                q.getTitle(),
                q.getDescription(),
                q.getCategory(),
                q.getStatus().getCode(),
                AssigneeSummary.of(q.getTester()),
                AssigneeSummary.of(q.getAssignee1()),
                AssigneeSummary.of(q.getAssignee2()),
                q.getPriority().getCode(),
                q.getImages().stream().map(QaItemImage::getImageUrl).toList(),
                githubIssue,
                q.getCreatedAt() != null ? q.getCreatedAt().toString() : null,
                q.getUpdatedAt() != null ? q.getUpdatedAt().toString() : null
            );
        }
    }

    /** 페이징 목록 응답. */
    public record PageResponse(
        List<Response> content,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) {}

    /** 대시보드 수치 집계. mine 조회 시 모든 수치가 '내 작업(테스터/담당자)' 기준으로 계산된다. */
    public record DashboardStats(
        long total,
        long needsFix,
        long inProgress,
        long fixDone,
        long confirmed,
        long onHold,
        long needsRecheck,
        long critical,
        List<ProjectSummary> byProject
    ) {}

    /** 프로젝트별 QA 개수/해결(수정완료+확인완료) 개수/수정필요 개수(사이드바 배지). */
    public record ProjectSummary(Long projectId, long count, long resolved, long needsFix) {}

    public record CreateRequest(
        @NotNull Long updateId,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 4000) String description,
        @Size(max = 50) String category,
        @NotNull QaStatus status,
        /** null 이면 현재 로그인 사용자를 tester 로 자동 지정. */
        Long testerId,
        Long assignee1Id,
        Long assignee2Id,
        @NotNull QaPriority priority,
        List<@Size(max = 800) String> images,
        /** true 면 프로젝트에 연결된 GitHub repo 에 이슈도 생성 (커밋 후 비동기). */
        Boolean createGithubIssue,
        /** 이슈를 생성할 repo (프로젝트에 여러 repo 가 연결된 경우). 생략 시 첫 번째 연결 repo. */
        @Size(max = 100) String githubRepoOwner,
        @Size(max = 200) String githubRepoName
    ) {}

    public record UpdateRequest(
        /** 다른 업데이트(버전)로 이동. null 이면 '변경 없음'. */
        Long updateId,
        @Size(max = 200) String title,
        @Size(max = 4000) String description,
        @Size(max = 50) String category,
        QaStatus status,
        Long testerId,
        Long assignee1Id,
        Long assignee2Id,
        QaPriority priority,
        List<@Size(max = 800) String> images,
        /** 명시적으로 비우려면 true. (null 인 채로 보내면 '변경 없음' 으로 간주) */
        Boolean clearTester,
        Boolean clearAssignee1,
        Boolean clearAssignee2
    ) {}

    public record HistoryResponse(
        Long id,
        String field,
        String oldValue,
        String newValue,
        AssigneeSummary changedBy,
        String changedAt
    ) {
        public static HistoryResponse from(QaHistory h) {
            return new HistoryResponse(
                h.getId(), h.getField(), h.getOldValue(), h.getNewValue(),
                AssigneeSummary.of(h.getChangedBy()),
                h.getChangedAt() != null ? h.getChangedAt().toString() : null
            );
        }
    }
}
