package com.qamanager.integration.github;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml 의 app.github.* 매핑.
 *
 * GitHub App 자격증명(app id / private key)은 여기 없다 — Manifest flow 로 런타임에 생성되어
 * DB(github_app 테이블)에 저장된다. 이 설정은 base url(GHES 대응)과 timeout 만 다룬다.
 */
@ConfigurationProperties(prefix = "app.github")
public record GithubProperties(
    /** 웹 UI base (앱 생성/설치 페이지). GHES 면 해당 서버 주소. */
    String webBaseUrl,
    /** REST API base. GHES 면 https://HOST/api/v3 형태. */
    String apiBaseUrl,
    int connectTimeoutSeconds,
    int readTimeoutSeconds
) {
    public String effectiveWebBaseUrl() {
        return trimSlash(notBlank(webBaseUrl) ? webBaseUrl : "https://github.com");
    }

    public String effectiveApiBaseUrl() {
        return trimSlash(notBlank(apiBaseUrl) ? apiBaseUrl : "https://api.github.com");
    }

    private static String trimSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
