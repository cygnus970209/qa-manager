package com.qamanager.notification.teams;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Bot Framework Connector 클라이언트 (프로액티브 1:1 메세지 발송).
 *
 * 흐름:
 *   1) {@link #createConversation(String, String)} : aadObjectId -> 봇과의 1:1 conversation id
 *   2) {@link #sendAdaptiveCard(String, String, String, Map)} : Adaptive Card activity 발송
 *
 * 인증: Bot Connector token (scope = api.botframework.com/.default). Graph token 과 별개로 캐시한다.
 *
 * 전제: 대상 사용자가 봇을 personal scope 에 설치해야 한다. 미설치/차단 시 403 ->
 *       {@link TeamsApiException#isBotNotInstalled()} true.
 */
@Component
@EnableConfigurationProperties(TeamsProperties.class)
public class TeamsBotClient {

    private static final Logger log = LoggerFactory.getLogger(TeamsBotClient.class);

    private static final String LOGIN_BASE = "https://login.microsoftonline.com";
    private static final String BOT_SCOPE = "https://api.botframework.com/.default";

    private final TeamsProperties props;
    private final RestClient http;
    private final JsonMapper mapper = JsonMapper.builder().build();

    private final ReentrantLock tokenLock = new ReentrantLock();
    private volatile String cachedToken;
    private volatile Instant tokenExpiresAt = Instant.EPOCH;

    public TeamsBotClient(TeamsProperties props) {
        this.props = props;
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(Duration.ofSeconds(props.connectTimeoutSeconds() > 0 ? props.connectTimeoutSeconds() : 5));
        rf.setReadTimeout(Duration.ofSeconds(props.readTimeoutSeconds() > 0 ? props.readTimeoutSeconds() : 10));
        this.http = RestClient.builder().requestFactory(rf).build();
    }

    /* ─────────────── Token ─────────────── */

    /** Bot Connector token 발급. 만료 60초 전 갱신. */
    public String acquireBotToken() {
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
            form.add("client_id", props.botAppId());
            form.add("client_secret", props.botAppPassword());
            form.add("scope", BOT_SCOPE);

            String url = LOGIN_BASE + "/" + props.effectiveBotTenantId() + "/oauth2/v2.0/token";
            String body = http.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(String.class);
            JsonNode node = mapper.readTree(body);
            String token = node.path("access_token").asText(null);
            int expiresIn = node.path("expires_in").asInt(3600);
            if (token == null) throw new TeamsApiException("Bot token 발급 실패: 응답에 access_token 없음");
            this.cachedToken = token;
            this.tokenExpiresAt = Instant.now().plusSeconds(expiresIn);
            return token;
        } catch (RestClientResponseException e) {
            throw new TeamsApiException("Bot token 발급 HTTP 오류: " + e.getStatusCode() + " " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            if (e instanceof TeamsApiException te) throw te;
            throw new TeamsApiException("Bot token 발급 실패", e);
        } finally {
            tokenLock.unlock();
        }
    }

    /* ─────────────── Conversation ─────────────── */

    /**
     * 봇과 대상 사용자(aadObjectId) 사이의 1:1 conversation 을 생성하고 conversation id 를 반환한다.
     * serviceUrl 은 인바운드 이벤트에서 캐시한 값을 우선 쓰고, 없으면 글로벌 기본값을 넘긴다.
     *
     * 주의: Teams 는 email/UPN 으로는 프로액티브 생성을 지원하지 않으므로 반드시 aadObjectId 를 쓴다.
     */
    public String createConversation(String aadObjectId, String serviceUrl) {
        if (aadObjectId == null || aadObjectId.isBlank()) {
            throw new TeamsApiException("aadObjectId 가 비어있음");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("bot", Map.of("id", botId()));
        payload.put("members", List.of(Map.of("id", aadObjectId)));
        payload.put("channelData", Map.of("tenant", Map.of("id", props.tenantId())));

        String uri = base(serviceUrl) + "/v3/conversations";
        try {
            String body = http.post()
                .uri(uri)
                .header("Authorization", "Bearer " + acquireBotToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(String.class);
            JsonNode node = mapper.readTree(body);
            String conversationId = node.path("id").asText(null);
            if (conversationId == null) throw new TeamsApiException("conversation 생성 응답에 id 없음: " + body);
            return conversationId;
        } catch (RestClientResponseException e) {
            throw translate("conversation 생성 실패 (aad=" + aadObjectId + ")", e);
        } catch (Exception e) {
            if (e instanceof TeamsApiException te) throw te;
            throw new TeamsApiException("conversation 생성 실패 (aad=" + aadObjectId + ")", e);
        }
    }

    /* ─────────────── Message ─────────────── */

    /** Adaptive Card 첨부 activity 발송. card 는 JSON Map (string 화하지 않고 객체 그대로 넣는다). */
    public void sendAdaptiveCard(String serviceUrl, String conversationId, String fallbackText, Map<String, Object> card) {
        Map<String, Object> attachment = new LinkedHashMap<>();
        attachment.put("contentType", "application/vnd.microsoft.card.adaptive");
        attachment.put("content", card);

        Map<String, Object> activity = new LinkedHashMap<>();
        activity.put("type", "message");
        if (fallbackText != null && !fallbackText.isBlank()) {
            activity.put("text", fallbackText);
        }
        activity.put("attachments", List.of(attachment));
        sendActivity(serviceUrl, conversationId, activity);
    }

    /** 임의 activity 발송. */
    public void sendActivity(String serviceUrl, String conversationId, Map<String, Object> activity) {
        String uri = base(serviceUrl) + "/v3/conversations/" + conversationId + "/activities";
        try {
            http.post()
                .uri(uri)
                .header("Authorization", "Bearer " + acquireBotToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(activity)
                .retrieve()
                .toBodilessEntity();
        } catch (RestClientResponseException e) {
            throw translate("activity 발송 실패 (conv=" + conversationId + ")", e);
        } catch (Exception e) {
            if (e instanceof TeamsApiException te) throw te;
            throw new TeamsApiException("activity 발송 실패 (conv=" + conversationId + ")", e);
        }
    }

    /* ─────────────── helpers ─────────────── */

    private String botId() {
        return "28:" + props.botAppId();
    }

    /** serviceUrl 정규화: null/blank 면 글로벌 기본값, 끝 슬래시 제거. */
    private String base(String serviceUrl) {
        String s = (serviceUrl == null || serviceUrl.isBlank()) ? props.effectiveServiceUrlDefault() : serviceUrl;
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    /** 403(봇 미설치/차단)을 식별해 TeamsApiException 으로 변환. */
    private TeamsApiException translate(String prefix, RestClientResponseException e) {
        String resp = e.getResponseBodyAsString();
        boolean notInstalled = e.getStatusCode().value() == 403
            && (resp.contains("ForbiddenOperationException")
                || resp.contains("MessageWritesBlocked")
                || resp.contains("BotNotInConversationRoster"));
        String msg = prefix + ": " + e.getStatusCode() + " " + resp;
        if (notInstalled) {
            msg = prefix + ": 봇이 사용자의 Teams 에 설치되지 않았거나 차단됨 (사용자가 봇 앱을 추가해야 발송 가능). "
                + e.getStatusCode();
        }
        return new TeamsApiException(msg, e, notInstalled);
    }
}
