package com.qamanager.notification.teams;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Microsoft Graph API client for Teams 1:1 messaging (Application permission).
 *
 * 주요 흐름:
 *   1) {@link #findUserIdByEmail(String)}      : email -> AAD Object ID
 *   2) {@link #createOneOnOneChat(String)}     : 봇 user 와의 oneOnOne chat 생성/조회 -> chat id
 *   3) {@link #sendChatMessage(String, String)} : Adaptive Card 또는 HTML body 발송
 *
 * Token 은 만료 60초 전부터 갱신 (single ReentrantLock 으로 동시 호출 방지).
 *
 * 주의: chat 생성은 application permission 으로 "members[].roles = [\"owner\"]" 가 필요하며,
 *      members 는 bot user + target user 2명. tenant 외부 user 는 불가.
 */
@Component
@EnableConfigurationProperties(TeamsProperties.class)
public class TeamsGraphClient {

    private static final Logger log = LoggerFactory.getLogger(TeamsGraphClient.class);

    private static final String LOGIN_BASE = "https://login.microsoftonline.com";
    private static final String GRAPH_BASE = "https://graph.microsoft.com/v1.0";

    private final TeamsProperties props;
    private final RestClient http;
    private final ObjectMapper mapper = new ObjectMapper();

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
            throw new TeamsApiException("user 조회 실패 (" + email + ")", e);
        }
    }

    /* ─────────────── Chat ─────────────── */

    /**
     * 봇 user 와 대상 user 사이의 oneOnOne chat 을 생성/조회한다.
     * Graph 가 이미 존재하는 chat 을 반환하므로 idempotent.
     */
    public String createOneOnOneChat(String targetUserId) {
        if (targetUserId == null || targetUserId.isBlank()) {
            throw new TeamsApiException("targetUserId 가 비어있음");
        }
        Map<String, Object> payload = Map.of(
            "chatType", "oneOnOne",
            "members", List.of(
                memberSpec(props.botUserOid()),
                memberSpec(targetUserId)
            )
        );
        try {
            String body = http.post()
                .uri(GRAPH_BASE + "/chats")
                .header("Authorization", "Bearer " + acquireToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(String.class);
            JsonNode node = mapper.readTree(body);
            String chatId = node.path("id").asText(null);
            if (chatId == null) throw new TeamsApiException("chat 생성 응답에 id 없음: " + body);
            return chatId;
        } catch (RestClientResponseException e) {
            throw new TeamsApiException("chat 생성 실패 (target=" + targetUserId + "): "
                + e.getStatusCode() + " " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            throw new TeamsApiException("chat 생성 실패 (target=" + targetUserId + ")", e);
        }
    }

    private Map<String, Object> memberSpec(String userId) {
        return Map.of(
            "@odata.type", "#microsoft.graph.aadUserConversationMember",
            "roles", List.of("owner"),
            "user@odata.bind", "https://graph.microsoft.com/v1.0/users('" + userId + "')"
        );
    }

    /* ─────────────── Message ─────────────── */

    /** HTML 메세지 발송. 간단한 경우 사용. */
    public void sendChatMessage(String chatId, String html) {
        Map<String, Object> payload = Map.of(
            "body", Map.of("contentType", "html", "content", html)
        );
        postMessage(chatId, payload);
    }

    /** Adaptive Card 첨부 메세지 발송. card 는 JSON Map. */
    public void sendAdaptiveCard(String chatId, String fallbackText, Map<String, Object> card) {
        // Graph 에서 Adaptive Card 는 message.attachments + body 의 <attachment id> 마커로 묶는다.
        String attachmentId = "1";
        try {
            String cardJson = mapper.writeValueAsString(card);
            Map<String, Object> payload = Map.of(
                "body", Map.of(
                    "contentType", "html",
                    "content", "<attachment id=\"" + attachmentId + "\"></attachment>"
                ),
                "attachments", List.of(Map.of(
                    "id", attachmentId,
                    "contentType", "application/vnd.microsoft.card.adaptive",
                    "contentUrl", null,
                    "content", cardJson,
                    "name", fallbackText,
                    "thumbnailUrl", null
                ))
            );
            postMessage(chatId, payload);
        } catch (Exception e) {
            if (e instanceof TeamsApiException te) throw te;
            throw new TeamsApiException("Adaptive Card 직렬화 실패", e);
        }
    }

    private void postMessage(String chatId, Map<String, Object> payload) {
        try {
            http.post()
                .uri(GRAPH_BASE + "/chats/" + chatId + "/messages")
                .header("Authorization", "Bearer " + acquireToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toBodilessEntity();
        } catch (RestClientResponseException e) {
            throw new TeamsApiException("메세지 발송 실패 (chat=" + chatId + "): "
                + e.getStatusCode() + " " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            throw new TeamsApiException("메세지 발송 실패 (chat=" + chatId + ")", e);
        }
    }
}
