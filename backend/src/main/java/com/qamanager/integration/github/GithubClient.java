package com.qamanager.integration.github;

import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.security.PrivateKey;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * GitHub REST API 클라이언트 (GitHub App 인증).
 *
 * 인증 2단계:
 *  1) app JWT (RS256, private key 서명) — /app/* 및 manifest conversion 이후 앱 수준 호출
 *  2) installation token (1시간 만료) — repo 수준 호출 (이슈 생성/상태 변경/타임라인 조회)
 *     installation 별로 캐시하고 만료 60초 전 갱신한다. ({@code TeamsBotClient} 의 토큰 캐싱 패턴)
 */
@Component
@EnableConfigurationProperties(GithubProperties.class)
public class GithubClient {

    private static final Logger log = LoggerFactory.getLogger(GithubClient.class);

    private static final String API_VERSION = "2022-11-28";
    private static final String ACCEPT = "application/vnd.github+json";
    /** 타임라인/repo 목록 페이지네이션 상한 (폭주 방지). */
    private static final int MAX_PAGES = 5;
    /** 커밋 상세 조회 상한 (이슈 하나에 커밋이 비정상적으로 많을 때 방어). */
    private static final int MAX_COMMITS = 30;

    private final GithubProperties props;
    private final RestClient http;
    private final JsonMapper mapper = JsonMapper.builder().build();

    private record CachedToken(String token, Instant expiresAt) {}
    private final Map<Long, CachedToken> tokenCache = new ConcurrentHashMap<>();
    private final ReentrantLock tokenLock = new ReentrantLock();

    public GithubClient(GithubProperties props) {
        this.props = props;
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(Duration.ofSeconds(props.connectTimeoutSeconds() > 0 ? props.connectTimeoutSeconds() : 5));
        rf.setReadTimeout(Duration.ofSeconds(props.readTimeoutSeconds() > 0 ? props.readTimeoutSeconds() : 10));
        this.http = RestClient.builder().requestFactory(rf).build();
    }

    /* ─────────────── Manifest conversion ─────────────── */

    /** Manifest flow 의 code 를 앱 자격증명으로 교환한다. 인증 불필요 (code 자체가 1회용 인증). */
    public GithubDto.ManifestConversion convertManifest(String code) {
        String uri = api() + "/app-manifests/" + code + "/conversions";
        try {
            String body = http.post()
                .uri(uri)
                .header("Accept", ACCEPT)
                .header("X-GitHub-Api-Version", API_VERSION)
                .retrieve()
                .body(String.class);
            JsonNode node = mapper.readTree(body);
            long appId = node.path("id").asLong(0);
            String pem = node.path("pem").asText(null);
            if (appId == 0 || pem == null) {
                throw new GithubApiException("manifest conversion 응답에 id/pem 없음");
            }
            return new GithubDto.ManifestConversion(
                appId,
                node.path("slug").asText(null),
                node.path("name").asText(null),
                node.path("html_url").asText(null),
                node.path("client_id").asText(null),
                node.path("client_secret").asText(null),
                node.path("webhook_secret").asText(null),
                pem
            );
        } catch (RestClientResponseException e) {
            throw translate("manifest conversion 실패 (code 만료 가능 — 앱 생성을 다시 시도하세요)", e);
        }
    }

    /* ─────────────── 인증 ─────────────── */

    /** 앱 수준 인증용 JWT. GitHub 제한상 exp 최대 10분 — 9분으로 발급, 캐시하지 않는다. */
    private String appJwt(GithubApp app) {
        PrivateKey key = GithubPrivateKeys.parse(app.getPem());
        Instant now = Instant.now();
        return Jwts.builder()
            .issuer(String.valueOf(app.getAppId()))
            .issuedAt(Date.from(now.minusSeconds(60)))   // 시계 오차 보정
            .expiration(Date.from(now.plusSeconds(540)))
            .signWith(key, Jwts.SIG.RS256)
            .compact();
    }

    /** installation token 발급 (캐시, 만료 60초 전 갱신). */
    public String installationToken(GithubApp app, long installationId) {
        CachedToken cached = tokenCache.get(installationId);
        if (cached != null && Instant.now().isBefore(cached.expiresAt().minusSeconds(60))) {
            return cached.token();
        }
        tokenLock.lock();
        try {
            cached = tokenCache.get(installationId);
            if (cached != null && Instant.now().isBefore(cached.expiresAt().minusSeconds(60))) {
                return cached.token();
            }
            String uri = api() + "/app/installations/" + installationId + "/access_tokens";
            String body = http.post()
                .uri(uri)
                .header("Authorization", "Bearer " + appJwt(app))
                .header("Accept", ACCEPT)
                .header("X-GitHub-Api-Version", API_VERSION)
                .retrieve()
                .body(String.class);
            JsonNode node = mapper.readTree(body);
            String token = node.path("token").asText(null);
            String expiresAt = node.path("expires_at").asText(null);
            if (token == null) throw new GithubApiException("installation token 응답에 token 없음");
            Instant exp = expiresAt != null ? Instant.parse(expiresAt) : Instant.now().plusSeconds(3600);
            tokenCache.put(installationId, new CachedToken(token, exp));
            return token;
        } catch (RestClientResponseException e) {
            throw translate("installation token 발급 실패 (installation=" + installationId + ")", e);
        } finally {
            tokenLock.unlock();
        }
    }

    /* ─────────────── App / Installation ─────────────── */

    /** 이 앱의 모든 installation id. */
    public List<Long> listInstallationIds(GithubApp app) {
        List<Long> ids = new ArrayList<>();
        String jwt = appJwt(app);
        for (int page = 1; page <= MAX_PAGES; page++) {
            String uri = api() + "/app/installations?per_page=100&page=" + page;
            JsonNode arr = getJson(uri, "Bearer " + jwt, "installation 목록 조회 실패");
            for (JsonNode n : arr) {
                long id = n.path("id").asLong(0);
                if (id > 0) ids.add(id);
            }
            if (arr.size() < 100) break;
        }
        return ids;
    }

    /** 특정 repo 에 접근 가능한 installation 조회 (repo 이관/재설치에도 안전). 미설치면 empty. */
    public Optional<Long> findRepoInstallation(GithubApp app, String owner, String repo) {
        String uri = api() + "/repos/" + owner + "/" + repo + "/installation";
        try {
            String body = http.get()
                .uri(uri)
                .header("Authorization", "Bearer " + appJwt(app))
                .header("Accept", ACCEPT)
                .header("X-GitHub-Api-Version", API_VERSION)
                .retrieve()
                .body(String.class);
            long id = mapper.readTree(body).path("id").asLong(0);
            return id > 0 ? Optional.of(id) : Optional.empty();
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) return Optional.empty();
            throw translate("repo installation 조회 실패 (" + owner + "/" + repo + ")", e);
        }
    }

    /** installation 에 연결된 repo 목록. */
    public List<GithubDto.Repo> listInstallationRepos(GithubApp app, long installationId) {
        List<GithubDto.Repo> repos = new ArrayList<>();
        String auth = "Bearer " + installationToken(app, installationId);
        for (int page = 1; page <= MAX_PAGES; page++) {
            String uri = api() + "/installation/repositories?per_page=100&page=" + page;
            JsonNode node = getJson(uri, auth, "repo 목록 조회 실패 (installation=" + installationId + ")");
            JsonNode arr = node.path("repositories");
            for (JsonNode r : arr) {
                repos.add(new GithubDto.Repo(
                    installationId,
                    r.path("owner").path("login").asText(null),
                    r.path("name").asText(null),
                    r.path("full_name").asText(null),
                    r.path("private").asBoolean(false),
                    r.path("html_url").asText(null)
                ));
            }
            if (arr.size() < 100) break;
        }
        return repos;
    }

    /* ─────────────── Issue ─────────────── */

    public GithubDto.IssueRef createIssue(GithubApp app, long installationId,
                                          String owner, String repo, String title, String body) {
        String uri = api() + "/repos/" + owner + "/" + repo + "/issues";
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", title);
        if (body != null && !body.isBlank()) payload.put("body", body);
        try {
            String resp = http.post()
                .uri(uri)
                .header("Authorization", "Bearer " + installationToken(app, installationId))
                .header("Accept", ACCEPT)
                .header("X-GitHub-Api-Version", API_VERSION)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(String.class);
            JsonNode node = mapper.readTree(resp);
            int number = node.path("number").asInt(0);
            if (number == 0) throw new GithubApiException("이슈 생성 응답에 number 없음");
            return new GithubDto.IssueRef(number, node.path("html_url").asText(null), node.path("state").asText("open"));
        } catch (RestClientResponseException e) {
            throw translate("이슈 생성 실패 (" + owner + "/" + repo + ")", e);
        }
    }

    /** state: "open" | "closed" */
    public void setIssueState(GithubApp app, long installationId,
                              String owner, String repo, int issueNumber, String state) {
        String uri = api() + "/repos/" + owner + "/" + repo + "/issues/" + issueNumber;
        try {
            http.patch()
                .uri(uri)
                .header("Authorization", "Bearer " + installationToken(app, installationId))
                .header("Accept", ACCEPT)
                .header("X-GitHub-Api-Version", API_VERSION)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("state", state))
                .retrieve()
                .toBodilessEntity();
        } catch (RestClientResponseException e) {
            throw translate("이슈 상태 변경 실패 (" + owner + "/" + repo + "#" + issueNumber + " → " + state + ")", e);
        }
    }

    /**
     * 이슈 타임라인에서 이 이슈를 참조한 커밋을 수집한다.
     * 커밋 메시지에 #N 을 적으면 GitHub 이 "referenced"(또는 close 키워드면 "closed") 이벤트를 남긴다.
     */
    public List<GithubDto.Commit> listIssueCommits(GithubApp app, long installationId,
                                                   String owner, String repo, int issueNumber) {
        String auth = "Bearer " + installationToken(app, installationId);
        Set<String> shas = new LinkedHashSet<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            String uri = api() + "/repos/" + owner + "/" + repo + "/issues/" + issueNumber
                + "/timeline?per_page=100&page=" + page;
            JsonNode arr = getJson(uri, auth, "이슈 타임라인 조회 실패 (" + owner + "/" + repo + "#" + issueNumber + ")");
            for (JsonNode ev : arr) {
                String event = ev.path("event").asText("");
                String sha = ev.path("commit_id").asText(null);
                if (sha != null && ("referenced".equals(event) || "closed".equals(event))) {
                    shas.add(sha);
                }
            }
            if (arr.size() < 100) break;
        }

        List<GithubDto.Commit> commits = new ArrayList<>();
        for (String sha : shas) {
            if (commits.size() >= MAX_COMMITS) {
                log.debug("이슈 참조 커밋 {}개 초과 — 이후 커밋 생략 ({}/{}#{})", MAX_COMMITS, owner, repo, issueNumber);
                break;
            }
            try {
                JsonNode c = getJson(api() + "/repos/" + owner + "/" + repo + "/commits/" + sha,
                    auth, "커밋 조회 실패 (" + sha + ")");
                commits.add(new GithubDto.Commit(
                    sha,
                    sha.length() > 7 ? sha.substring(0, 7) : sha,
                    c.path("commit").path("message").asText(""),
                    c.path("commit").path("author").path("name").asText(null),
                    c.path("author").path("login").asText(null),
                    c.path("author").path("avatar_url").asText(null),
                    c.path("html_url").asText(null),
                    c.path("commit").path("author").path("date").asText(null)
                ));
            } catch (GithubApiException e) {
                // 다른 repo 에서 참조된 커밋 등 조회 불가 케이스는 건너뛴다.
                log.debug("참조 커밋 조회 생략 (sha={}): {}", sha, e.getMessage());
            }
        }
        return commits;
    }

    /* ─────────────── helpers ─────────────── */

    private JsonNode getJson(String uri, String authorization, String errorPrefix) {
        try {
            String body = http.get()
                .uri(uri)
                .header("Authorization", authorization)
                .header("Accept", ACCEPT)
                .header("X-GitHub-Api-Version", API_VERSION)
                .retrieve()
                .body(String.class);
            return mapper.readTree(body);
        } catch (RestClientResponseException e) {
            throw translate(errorPrefix, e);
        }
    }

    private String api() {
        return props.effectiveApiBaseUrl();
    }

    private GithubApiException translate(String prefix, RestClientResponseException e) {
        return new GithubApiException(prefix + ": " + e.getStatusCode().value() + " " + e.getResponseBodyAsString(), e);
    }
}
