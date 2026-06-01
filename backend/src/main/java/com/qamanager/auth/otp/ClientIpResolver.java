package com.qamanager.auth.otp;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * 보안 판단(신뢰 IP 매칭)에 쓸 클라이언트 IP 추출기.
 *
 * X-Forwarded-For 는 클라이언트가 위조할 수 있으므로, 신뢰 프록시 hop 수만큼
 * 오른쪽(우리 인프라가 append 한 값) 기준으로 실제 클라이언트를 고른다.
 * nginx 1단 + `$proxy_add_x_forwarded_for` 구성 가정: 가장 오른쪽 값이 nginx 가 본 remote_addr.
 *
 * ⚠️ 전제: nginx 등 신뢰 프록시가 클라이언트가 보낸 XFF 를 그대로 흘리지 않고
 *          자신이 본 remote_addr 를 append/덮어쓰도록 구성돼 있어야 한다(docs/SECURITY_IP_OTP_LOGIN.md 4.3).
 */
@Component
public class ClientIpResolver {

    /** @param trustedProxyCount 신뢰 프록시 hop 수(>=1). */
    public String resolve(HttpServletRequest req, int trustedProxyCount) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String[] parts = xff.split(",");
            int hops = Math.max(1, trustedProxyCount);
            int idx = parts.length - hops; // 오른쪽에서 hops 번째 = 실제 클라이언트
            if (idx < 0) idx = 0;
            String ip = parts[idx].trim();
            if (!ip.isEmpty()) return ip;
        }
        return req.getRemoteAddr();
    }
}
