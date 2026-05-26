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
}
