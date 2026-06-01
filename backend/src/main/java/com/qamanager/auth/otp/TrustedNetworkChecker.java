package com.qamanager.auth.otp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 클라이언트 IP 가 신뢰 대역(CIDR allowlist)에 속하는지 판정.
 * 설정값은 부팅 시 한 번 IpAddressMatcher 로 컴파일한다.
 */
@Component
public class TrustedNetworkChecker {

    private static final Logger log = LoggerFactory.getLogger(TrustedNetworkChecker.class);

    private final List<IpAddressMatcher> matchers;

    public TrustedNetworkChecker(SecurityOtpProperties props) {
        this.matchers = props.trustedCidrList().stream()
            .map(this::compile)
            .filter(java.util.Objects::nonNull)
            .toList();
        log.info("신뢰 IP 대역 {}개 로드됨", matchers.size());
    }

    private IpAddressMatcher compile(String cidr) {
        try {
            return new IpAddressMatcher(cidr);
        } catch (IllegalArgumentException e) {
            log.warn("잘못된 신뢰 CIDR 무시: {} ({})", cidr, e.getMessage());
            return null;
        }
    }

    public boolean isTrusted(String ip) {
        if (ip == null || ip.isBlank() || matchers.isEmpty()) return false;
        for (IpAddressMatcher m : matchers) {
            try {
                if (m.matches(ip)) return true;
            } catch (IllegalArgumentException ignored) {
                // IP 형식 불일치(IPv4/IPv6) 시 다음 matcher 로
            }
        }
        return false;
    }
}
