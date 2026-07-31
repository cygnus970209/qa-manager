package com.qamanager.integration.github;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class GithubDto {

    /** GET /api/github/app — 연동 상태. installUrl 은 앱 설치/repo 권한 관리 페이지. */
    public record AppStatus(
        boolean configured,
        String appSlug,
        String appName,
        String installUrl
    ) {}

    /** POST /api/github/app/manifest 요청. organization 이 없으면 개인 계정에 앱 생성. */
    public record ManifestRequest(
        @Size(max = 100) String organization,
        /** 프론트 origin (redirect_url 구성용). */
        @NotBlank @Size(max = 300) String baseUrl
    ) {}

    /** manifest 는 targetUrl 로 form POST (필드명 "manifest") 해야 한다 — 프론트에서 hidden form submit. */
    public record ManifestResponse(String targetUrl, String manifest) {}

    /** POST /api/github/app/conversion — GitHub 이 redirect_url 로 돌려준 code 를 교환. */
    public record ConversionRequest(@NotBlank String code) {}

    /** 설치된 repo. 프로젝트 연결 드롭다운용. */
    public record Repo(
        long installationId,
        String owner,
        String name,
        String fullName,
        @JsonProperty("private") boolean isPrivate,
        String htmlUrl
    ) {}

    /** GitHub API 이슈 참조 (생성 응답). */
    public record IssueRef(int number, String htmlUrl, String state) {}

    /** 이슈 타임라인에서 수집한 참조 커밋. */
    public record Commit(
        String sha,
        String shortSha,
        String message,
        String authorName,
        String authorLogin,
        String avatarUrl,
        String htmlUrl,
        String committedAt
    ) {}

    /** manifest conversion (POST /app-manifests/{code}/conversions) 응답 매핑. */
    public record ManifestConversion(
        long appId,
        String slug,
        String name,
        String htmlUrl,
        String clientId,
        String clientSecret,
        String webhookSecret,
        String pem
    ) {}
}
