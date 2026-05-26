package com.qamanager.notification.teams;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigInteger;
import java.security.Key;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Bot Framework 가 /api/teams/messages 로 보내는 요청의 Authorization Bearer 토큰을 검증한다.
 *
 * 검증 항목:
 *  - RS256 서명 (Bot Framework OpenID 메타데이터의 JWKS 공개키로 검증; kid 매칭)
 *  - issuer == https://api.botframework.com
 *  - audience 에 우리 봇의 App ID 포함
 *
 * JWKS 는 메모리에 캐시하고, kid 미스 시에만(키 롤오버) 5분 throttle 로 재조회한다.
 * 이 엔드포인트는 SecurityFilterChain 에서 permitAll 이므로 이 검증이 유일한 인증 수단이다.
 */
@Component
@EnableConfigurationProperties(TeamsProperties.class)
public class TeamsBotJwtValidator {

    private static final Logger log = LoggerFactory.getLogger(TeamsBotJwtValidator.class);

    private static final String OPENID_CONFIG = "https://login.botframework.com/v1/.well-known/openidconfiguration";
    private static final String ISSUER = "https://api.botframework.com";
    private static final long REFRESH_THROTTLE_SECONDS = 300;
    /** 업계 표준 clock skew (Bot Framework 권장 5분). */
    private static final long CLOCK_SKEW_SECONDS = 300;

    private final TeamsProperties props;
    private final RestClient http;
    private final JsonMapper mapper = JsonMapper.builder().build();

    private volatile Map<String, PublicKey> keyCache = Map.of();
    private volatile Instant keysFetchedAt = Instant.EPOCH;
    private final ReentrantLock refreshLock = new ReentrantLock();

    public TeamsBotJwtValidator(TeamsProperties props) {
        this.props = props;
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(Duration.ofSeconds(props.connectTimeoutSeconds() > 0 ? props.connectTimeoutSeconds() : 5));
        rf.setReadTimeout(Duration.ofSeconds(props.readTimeoutSeconds() > 0 ? props.readTimeoutSeconds() : 10));
        this.http = RestClient.builder().requestFactory(rf).build();
    }

    /**
     * "Bearer xxx" 헤더를 검증한다. 실패 시 {@link TeamsApiException}.
     *
     * @param activityServiceUrl 인바운드 activity 의 serviceUrl. 토큰의 serviceUrl claim 과 대조한다
     *                           (serviceUrl 위조로 봇 토큰을 탈취하는 공격 방지). null 이면 대조 생략.
     */
    public void validate(String authorizationHeader, String activityServiceUrl) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new TeamsApiException("Authorization Bearer 토큰 없음");
        }
        String token = authorizationHeader.substring("Bearer ".length()).trim();
        try {
            Jws<Claims> jws = Jwts.parser()
                .keyLocator(header -> {
                    if (header instanceof JwsHeader jh) {
                        return resolveKey(jh.getKeyId());
                    }
                    return null;
                })
                .clockSkewSeconds(CLOCK_SKEW_SECONDS)
                .build()
                .parseSignedClaims(token);

            Claims c = jws.getPayload();
            if (!ISSUER.equals(c.getIssuer())) {
                throw new TeamsApiException("Bot 토큰 issuer 불일치: " + c.getIssuer());
            }
            Set<String> aud = c.getAudience();
            if (aud == null || !aud.contains(props.botAppId())) {
                throw new TeamsApiException("Bot 토큰 audience 불일치: " + aud);
            }
            // 요구사항 #7: 토큰의 serviceUrl claim 이 activity 의 serviceUrl 과 일치해야 한다.
            // claim 은 소문자 "serviceurl". 존재할 때만 대조 (일부 activity 는 미포함).
            String tokenServiceUrl = c.get("serviceurl", String.class);
            if (tokenServiceUrl != null && activityServiceUrl != null
                    && !tokenServiceUrl.equals(activityServiceUrl)) {
                throw new TeamsApiException("Bot 토큰 serviceUrl 불일치 (token=" + tokenServiceUrl
                    + ", activity=" + activityServiceUrl + ")");
            }
        } catch (TeamsApiException e) {
            throw e;
        } catch (Exception e) {
            throw new TeamsApiException("Bot 토큰 검증 실패: " + e.getMessage(), e);
        }
    }

    private Key resolveKey(String kid) {
        if (kid == null) return null;
        PublicKey k = keyCache.get(kid);
        if (k != null) return k;
        refreshKeys();            // 캐시 미스 → 키 롤오버 가능성, 재조회
        return keyCache.get(kid);
    }

    private void refreshKeys() {
        if (Instant.now().isBefore(keysFetchedAt.plusSeconds(REFRESH_THROTTLE_SECONDS))) {
            return; // 임의 kid 로 무한 재조회 유발 방지
        }
        refreshLock.lock();
        try {
            if (Instant.now().isBefore(keysFetchedAt.plusSeconds(REFRESH_THROTTLE_SECONDS))) {
                return;
            }
            JsonNode config = mapper.readTree(http.get().uri(OPENID_CONFIG).retrieve().body(String.class));
            String jwksUri = config.path("jwks_uri").asText(null);
            if (jwksUri == null) throw new TeamsApiException("openid-configuration 에 jwks_uri 없음");

            JsonNode jwks = mapper.readTree(http.get().uri(jwksUri).retrieve().body(String.class));
            Map<String, PublicKey> map = new HashMap<>();
            for (JsonNode key : jwks.path("keys")) {
                if (!"RSA".equals(key.path("kty").asText()) || !key.has("kid")) continue;
                try {
                    map.put(key.get("kid").asText(), toRsaPublicKey(key));
                } catch (Exception ex) {
                    log.warn("JWKS 키 파싱 실패 (kid={}): {}", key.path("kid").asText(), ex.getMessage());
                }
            }
            this.keyCache = map;
            this.keysFetchedAt = Instant.now();
        } catch (Exception e) {
            log.warn("Bot Framework JWKS 조회 실패: {}", e.getMessage());
        } finally {
            refreshLock.unlock();
        }
    }

    private PublicKey toRsaPublicKey(JsonNode jwk) throws Exception {
        byte[] n = Base64.getUrlDecoder().decode(jwk.get("n").asText());
        byte[] e = Base64.getUrlDecoder().decode(jwk.get("e").asText());
        RSAPublicKeySpec spec = new RSAPublicKeySpec(new BigInteger(1, n), new BigInteger(1, e));
        return KeyFactory.getInstance("RSA").generatePublic(spec);
    }
}
