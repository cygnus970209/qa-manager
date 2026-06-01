package com.qamanager.auth.otp;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;

/**
 * IP 기반 조건부 이메일 OTP 로그인 설정. (app.security.ip-otp.*)
 *
 * - enabled=false 면 전 구간 현행 동작(즉시 토큰). 롤백 스위치.
 * - trusted-cidrs 가 비면 모든 IP 가 OTP 대상이 된다(운영 배포 시 사무실 IP 필수).
 */
@ConfigurationProperties(prefix = "app.security.ip-otp")
public class SecurityOtpProperties {

    private boolean enabled = false;
    /** 콤마로 구분된 CIDR/IP 목록. 이 대역에서의 로그인은 OTP 면제. */
    private String trustedCidrs = "";
    /** 신뢰 프록시 hop 수(nginx 1단=1). X-Forwarded-For 오른쪽에서 N번째를 클라이언트로 채택. */
    private int trustedProxyCount = 1;
    /** OTP 메일 발신 표시 주소. */
    private String mailFrom = "QA Manager <no-reply@intocns.com>";
    private final Otp otp = new Otp();

    public static class Otp {
        private int length = 6;
        private long ttlSeconds = 600;
        private int maxAttempts = 5;
        private long resendCooldownSeconds = 60;

        public int getLength() { return length; }
        public void setLength(int length) { this.length = length; }
        public long getTtlSeconds() { return ttlSeconds; }
        public void setTtlSeconds(long ttlSeconds) { this.ttlSeconds = ttlSeconds; }
        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
        public long getResendCooldownSeconds() { return resendCooldownSeconds; }
        public void setResendCooldownSeconds(long resendCooldownSeconds) { this.resendCooldownSeconds = resendCooldownSeconds; }
    }

    /** 빈/공백을 걸러낸 신뢰 CIDR 목록. */
    public List<String> trustedCidrList() {
        if (trustedCidrs == null || trustedCidrs.isBlank()) return List.of();
        return Arrays.stream(trustedCidrs.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getTrustedCidrs() { return trustedCidrs; }
    public void setTrustedCidrs(String trustedCidrs) { this.trustedCidrs = trustedCidrs; }
    public int getTrustedProxyCount() { return trustedProxyCount; }
    public void setTrustedProxyCount(int trustedProxyCount) { this.trustedProxyCount = trustedProxyCount; }
    public String getMailFrom() { return mailFrom; }
    public void setMailFrom(String mailFrom) { this.mailFrom = mailFrom; }
    public Otp getOtp() { return otp; }
}
