package com.qamanager.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class ProjectDto {

    public record Response(
        Long id,
        String name,
        String description,
        String status,
        boolean pinned,
        Long githubInstallationId,
        String githubRepoOwner,
        String githubRepoName,
        String createdAt,
        String updatedAt
    ) {
        public static Response from(Project p, boolean pinned) {
            return new Response(
                p.getId(),
                p.getName(),
                p.getDescription(),
                p.getStatus().getCode(),
                pinned,
                p.getGithubInstallationId(),
                p.getGithubRepoOwner(),
                p.getGithubRepoName(),
                p.getCreatedAt() != null ? p.getCreatedAt().toString() : null,
                p.getUpdatedAt() != null ? p.getUpdatedAt().toString() : null
            );
        }
    }

    public record CreateRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 4000) String description,
        @NotNull ProjectStatus status
    ) {}

    public record UpdateRequest(
        @Size(max = 100) String name,
        @Size(max = 4000) String description,
        ProjectStatus status,
        /** GitHub repo 연결 — 세 필드를 함께 보낸다. null 이면 '변경 없음'. */
        Long githubInstallationId,
        @Size(max = 100) String githubRepoOwner,
        @Size(max = 200) String githubRepoName,
        /** repo 연결 해제는 true. */
        Boolean clearGithubRepo
    ) {}
}
