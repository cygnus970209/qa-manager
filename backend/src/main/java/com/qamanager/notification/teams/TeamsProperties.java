package com.qamanager.notification.teams;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml 의 app.teams.* 매핑.
 *
 * enabled=false 또는 핵심 필드(tenant/client/secret/bot) 누락 시 발송은 no-op.
 * 봇 user OID 는 oneOnOne chat 의 한쪽 멤버로 사용되며, 발송 메세지의 from 사용자가 된다.
 */
@ConfigurationProperties(prefix = "app.teams")
public record TeamsProperties(
    boolean enabled,
    String tenantId,
    String clientId,
    String clientSecret,
    String botUserOid,
    String emailDomain,
    int connectTimeoutSeconds,
    int readTimeoutSeconds
) {
    public boolean isUsable() {
        return enabled
            && tenantId != null && !tenantId.isBlank()
            && clientId != null && !clientId.isBlank()
            && clientSecret != null && !clientSecret.isBlank()
            && botUserOid != null && !botUserOid.isBlank();
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
