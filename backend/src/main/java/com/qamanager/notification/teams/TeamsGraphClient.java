package com.qamanager.notification.teams;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Microsoft Graph API client. email -> AAD Object ID 조회 전용.
 *
 * 메세지 발송은 Graph application permission 으로 불가하여 {@link TeamsBotClient} (Bot Framework) 로 옮겼다.
 * 이 클라이언트는 client_credentials 토큰으로 사용자 디렉터리 조회만 수행한다.
 *
 * Token 은 만료 60초 전부터 갱신 (single ReentrantLock 으로 동시 호출 방지).
 */
@Component
@EnableConfigurationProperties(TeamsProperties.class)
public class TeamsGraphClient {

    private static final Logger log = LoggerFactory.getLogger(TeamsGraphClient.class);

    private static final String LOGIN_BASE = "https://login.microsoftonline.com";
    private static final String GRAPH_BASE = "https://graph.microsoft.com/v1.0";

    private final TeamsProperties props;
    private final RestClient http;
    private final JsonMapper mapper = JsonMapper.builder().build();

    /** Token 캐시 (메모리). 단일 인스턴스 가정. */
    private final ReentrantLock tokenLock = new ReentrantLock();
    private volatile String cachedToken;
    private volatile Instant tokenExpiresAt = Instant.EPOCH;

    public TeamsGraphClient(TeamsProperties props) {
        this.props = props;
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(Duration.ofSeconds(props.connectTimeoutSeconds() > 0 ? props.connectTimeoutSeconds() : 5));
        rf.setReadTimeout(Duration.ofSeconds(props.readTimeoutSeconds() > 0 ? props.readTimeoutSeconds() : 10));
        this.http = RestClient.builder().requestFactory(rf).build();
    }

    public boolean isUsable() { return props.isUsable(); }

    /* ─────────────── Token ─────────────── */

    /** client_credentials flow 로 token 발급. 만료 60초 전 갱신. */
    public String acquireToken() {
        if (cachedToken != null && Instant.now().isBefore(tokenExpiresAt.minusSeconds(60))) {
            return cachedToken;
        }
        tokenLock.lock();
        try {
            if (cachedToken != null && Instant.now().isBefore(tokenExpiresAt.minusSeconds(60))) {
                return cachedToken;
            }
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "client_credentials");
            form.add("client_id", props.clientId());
            form.add("client_secret", props.clientSecret());
            form.add("scope", "https://graph.microsoft.com/.default");

            String url = LOGIN_BASE + "/" + props.tenantId() + "/oauth2/v2.0/token";
            String body = http.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(String.class);
            JsonNode node = mapper.readTree(body);
            String token = node.path("access_token").asText(null);
            int expiresIn = node.path("expires_in").asInt(3600);
            if (token == null) throw new TeamsApiException("token 발급 실패: 응답에 access_token 없음");
            this.cachedToken = token;
            this.tokenExpiresAt = Instant.now().plusSeconds(expiresIn);
            return token;
        } catch (RestClientResponseException e) {
            throw new TeamsApiException("token 발급 HTTP 오류: " + e.getStatusCode() + " " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            if (e instanceof TeamsApiException te) throw te;
            throw new TeamsApiException("token 발급 실패", e);
        } finally {
            tokenLock.unlock();
        }
    }

    /* ─────────────── User lookup ─────────────── */

    /**
     * email (UPN 또는 mail) 로 AAD Object ID 조회.
     * 사용자를 찾지 못하면 empty.
     */
    public Optional<String> findUserIdByEmail(String email) {
        if (email == null || email.isBlank()) return Optional.empty();
        try {
            String body = http.get()
                .uri(GRAPH_BASE + "/users/" + email + "?$select=id")
                .header("Authorization", "Bearer " + acquireToken())
                .retrieve()
                .body(String.class);
            JsonNode node = mapper.readTree(body);
            String id = node.path("id").asText(null);
            return Optional.ofNullable(id);
        } catch (RestClientResponseException e) {
            HttpStatusCode status = e.getStatusCode();
            if (status.value() == 404) return Optional.empty();
            throw new TeamsApiException("user 조회 실패 (" + email + "): " + status + " " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            if (e instanceof TeamsApiException te) throw te;
            throw new TeamsApiException("user 조회 실패 (" + email + ")", e);
        }
    }
}
