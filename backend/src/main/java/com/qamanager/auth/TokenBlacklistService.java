package com.qamanager.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * JWT 토큰 무효화 저장소.
 * Redis 에 토큰의 jti 를 잔여 만료시간만큼 보관.
 * - 로그아웃 시 access/refresh 모두 등록
 * - refresh rotation 시 이전 refresh 의 jti 를 등록 → 재사용 방지
 */
@Service
public class TokenBlacklistService {

    private static final Logger log = LoggerFactory.getLogger(TokenBlacklistService.class);
    private static final String PREFIX = "auth:blacklist:";

    private final StringRedisTemplate redis;
    private final JwtTokenProvider tokenProvider;

    public TokenBlacklistService(StringRedisTemplate redis, JwtTokenProvider tokenProvider) {
        this.redis = redis;
        this.tokenProvider = tokenProvider;
    }

    /** 토큰 자체에서 jti와 만료시각을 꺼내 블랙리스트에 등록. 이미 만료된 토큰은 무시. */
    public void blacklist(String token) {
        if (!StringUtils.hasText(token)) return;
        Claims claims;
        try {
            claims = tokenProvider.parse(token);
        } catch (JwtException e) {
            // 유효하지 않은 토큰은 굳이 블랙리스트할 필요 없음
            return;
        }
        String jti = tokenProvider.getJti(claims);
        if (!StringUtils.hasText(jti)) return;

        Date exp = tokenProvider.getExpiration(claims);
        long remainingMs = exp.getTime() - Instant.now().toEpochMilli();
        if (remainingMs <= 0) return;

        try {
            redis.opsForValue().set(PREFIX + jti, "1", Duration.ofMillis(remainingMs));
        } catch (Exception e) {
            // Redis 장애 시에도 로그아웃 자체는 실패시키지 않음 (쿠키는 이미 만료됨)
            log.warn("토큰 블랙리스트 등록 실패: {}", e.getMessage());
        }
    }

    public boolean isBlacklisted(String jti) {
        if (!StringUtils.hasText(jti)) return false;
        try {
            return Boolean.TRUE.equals(redis.hasKey(PREFIX + jti));
        } catch (Exception e) {
            // Redis 장애 시 보수적으로 통과 (가용성 우선). 운영에서는 알람 필요.
            log.warn("블랙리스트 조회 실패: {}", e.getMessage());
            return false;
        }
    }
}
