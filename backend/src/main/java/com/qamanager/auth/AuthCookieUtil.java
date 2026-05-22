package com.qamanager.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class AuthCookieUtil {

    public static final String ACCESS_COOKIE = "qam_access_token";
    public static final String REFRESH_COOKIE = "qam_refresh_token";

    private final boolean secure;
    private final String sameSite;
    private final JwtTokenProvider tokenProvider;

    public AuthCookieUtil(@Value("${app.cookie.secure:false}") boolean secure,
                          @Value("${app.cookie.same-site:Lax}") String sameSite,
                          JwtTokenProvider tokenProvider) {
        this.secure = secure;
        this.sameSite = sameSite;
        this.tokenProvider = tokenProvider;
    }

    public void writeAuthCookies(HttpServletResponse res, String accessToken, String refreshToken) {
        res.addHeader(HttpHeaders.SET_COOKIE, buildCookie(ACCESS_COOKIE, accessToken, tokenProvider.getAccessTtl()).toString());
        res.addHeader(HttpHeaders.SET_COOKIE, buildCookie(REFRESH_COOKIE, refreshToken, tokenProvider.getRefreshTtl()).toString());
    }

    public void clearAuthCookies(HttpServletResponse res) {
        res.addHeader(HttpHeaders.SET_COOKIE, buildCookie(ACCESS_COOKIE, "", Duration.ZERO).toString());
        res.addHeader(HttpHeaders.SET_COOKIE, buildCookie(REFRESH_COOKIE, "", Duration.ZERO).toString());
    }

    public String readAccessCookie(HttpServletRequest req) {
        return readCookie(req, ACCESS_COOKIE);
    }

    public String readRefreshCookie(HttpServletRequest req) {
        return readCookie(req, REFRESH_COOKIE);
    }

    private ResponseCookie buildCookie(String name, String value, Duration maxAge) {
        return ResponseCookie.from(name, value)
            .httpOnly(true)
            .secure(secure)
            .sameSite(sameSite)
            .path("/")
            .maxAge(maxAge)
            .build();
    }

    private static String readCookie(HttpServletRequest req, String name) {
        Cookie[] cookies = req.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (name.equals(c.getName())) return c.getValue();
        }
        return null;
    }
}
