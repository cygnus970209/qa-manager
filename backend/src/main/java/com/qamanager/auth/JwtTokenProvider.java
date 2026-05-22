package com.qamanager.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    private static final String CLAIM_TYPE = "typ";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";
    private static final String CLAIM_USERNAME = "username";

    private final SecretKey key;
    private final Duration accessTtl;
    private final Duration refreshTtl;

    public JwtTokenProvider(JwtProperties props) {
        byte[] keyBytes = props.secret().getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT secret must be at least 32 bytes long");
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.accessTtl = Duration.ofMinutes(props.accessTtlMinutes());
        this.refreshTtl = Duration.ofDays(props.refreshTtlDays());
    }

    public String createAccessToken(Long memberId, String username) {
        return buildToken(memberId, username, TYPE_ACCESS, accessTtl);
    }

    public String createRefreshToken(Long memberId, String username) {
        return buildToken(memberId, username, TYPE_REFRESH, refreshTtl);
    }

    public Duration getAccessTtl() {
        return accessTtl;
    }

    public Duration getRefreshTtl() {
        return refreshTtl;
    }

    public Claims parse(String token) {
        try {
            return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new JwtException("invalid token: " + e.getMessage(), e);
        }
    }

    public boolean isAccess(Claims claims) {
        return TYPE_ACCESS.equals(claims.get(CLAIM_TYPE));
    }

    public boolean isRefresh(Claims claims) {
        return TYPE_REFRESH.equals(claims.get(CLAIM_TYPE));
    }

    public String getUsername(Claims claims) {
        return (String) claims.get(CLAIM_USERNAME);
    }

    public Long getMemberId(Claims claims) {
        return Long.parseLong(claims.getSubject());
    }

    public String getJti(Claims claims) {
        return claims.getId();
    }

    public Date getExpiration(Claims claims) {
        return claims.getExpiration();
    }

    private String buildToken(Long memberId, String username, String type, Duration ttl) {
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(String.valueOf(memberId))
            .claim(CLAIM_USERNAME, username)
            .claim(CLAIM_TYPE, type)
            .id(UUID.randomUUID().toString())
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(ttl)))
            .signWith(key)
            .compact();
    }
}
