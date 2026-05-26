package com.qamanager.notification.teams;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml 의 app.teams.* 매핑.
 *
 * 두 가지 자격증명을 쓴다:
 *  - Graph 조회용 (tenantId/clientId/clientSecret): email -> AAD Object ID 변환. {@code .default} scope.
 *  - Bot Connector 발송용 (botAppId/botAppPassword/botTenantId): Bot Framework 프로액티브 메세지 발송.
 *
 * enabled=false 또는 핵심 필드 누락 시 발송은 no-op.
 * Graph API 의 chat 메세지 발송은 application permission 으로 불가하여 Bot Framework 로 전환했다.
 */
@ConfigurationProperties(prefix = "app.teams")
public record TeamsProperties(
    boolean enabled,
    // ── Graph (email -> AAD Object ID 조회) ──
    String tenantId,
    String clientId,
    String clientSecret,
    // ── Bot Connector (프로액티브 메세지 발송) ──
    String botAppId,
    String botAppPassword,
    /** Bot Connector 토큰 발급 tenant. 멀티테넌트 봇이면 "botframework.com", 싱글테넌트면 회사 tenant id. */
    String botTenantId,
    /** 인바운드 activity 의 serviceUrl 이 캐시에 없을 때 쓰는 글로벌 기본값. */
    String serviceUrlDefault,
    // ── 공통 ──
    String emailDomain,
    int connectTimeoutSeconds,
    int readTimeoutSeconds
) {
    public boolean isUsable() {
        return enabled
            && notBlank(tenantId)
            && notBlank(clientId)
            && notBlank(clientSecret)
            && notBlank(botAppId)
            && notBlank(botAppPassword);
    }

    /** Bot Connector 토큰 발급 tenant (미설정 시 멀티테넌트 기본값). */
    public String effectiveBotTenantId() {
        return notBlank(botTenantId) ? botTenantId : "botframework.com";
    }

    /** serviceUrl 글로벌 기본값 (미설정 시 public cloud). */
    public String effectiveServiceUrlDefault() {
        return notBlank(serviceUrlDefault) ? serviceUrlDefault : "https://smba.trafficmanager.net/teams/";
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /** 도메인 제약이 설정되어 있으면 해당 도메인으로 끝나는 email 만 매치. 없으면 email 형식만 검사. */
    public boolean matchesEmailDomain(String email) {
        if (email == null || email.isBlank()) return false;
        int at = email.indexOf('@');
        if (at <= 0 || at == email.length() - 1) return false;
        if (emailDomain == null || emailDomain.isBlank()) return true;
        String d = emailDomain.startsWith("@") ? emailDomain.substring(1) : emailDomain;
        return email.regionMatches(true, at + 1, d, 0, d.length())
            && email.length() - (at + 1) == d.length();
    }
}
