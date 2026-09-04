package com.qamanager.project;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public class ProjectDto {

    /** 프로젝트에 연결된 GitHub repo. */
    public record GithubRepoLink(Long installationId, String repoOwner, String repoName) {
        public static GithubRepoLink from(ProjectGithubRepo r) {
            return new GithubRepoLink(r.getInstallationId(), r.getRepoOwner(), r.getRepoName());
        }
    }

    public record Response(
        Long id,
        String name,
        String description,
        String status,
        boolean pinned,
        List<GithubRepoLink> githubRepos,
        String createdAt,
        String updatedAt
    ) {
        public static Response from(Project p, boolean pinned, List<GithubRepoLink> githubRepos) {
            return new Response(
                p.getId(),
                p.getName(),
                p.getDescription(),
                p.getStatus().getCode(),
                pinned,
                githubRepos == null ? List.of() : githubRepos,
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

    /** UpdateRequest.githubRepos 원소. */
    public record GithubRepoRef(
        @NotNull Long installationId,
        @NotBlank @Size(max = 100) String repoOwner,
        @NotBlank @Size(max = 200) String repoName
    ) {}

    public record UpdateRequest(
        @Size(max = 100) String name,
        @Size(max = 4000) String description,
        ProjectStatus status,
        /** GitHub repo 연결 목록 전체 교체. null 이면 '변경 없음', 빈 목록이면 전부 해제. */
        List<@Valid GithubRepoRef> githubRepos
    ) {}
    /** 사이드바 프로젝트 순서 저장 — 전체 id 배열 (사용자별). */
    public record ReorderRequest(@NotNull @Size(max = 500) List<Long> projectIds) {}
}
