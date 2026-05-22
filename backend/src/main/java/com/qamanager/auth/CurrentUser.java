package com.qamanager.auth;

import com.qamanager.common.ApiException;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * SecurityContext 에서 현재 로그인 사용자(AuthPrincipal)를 꺼내는 헬퍼.
 */
public final class CurrentUser {

    private CurrentUser() {}

    public static AuthPrincipal getOrThrow() {
        Object principal = SecurityContextHolder.getContext().getAuthentication() != null
            ? SecurityContextHolder.getContext().getAuthentication().getPrincipal()
            : null;
        if (principal instanceof AuthPrincipal p) {
            return p;
        }
        throw ApiException.unauthorized("로그인이 필요합니다.");
    }

    public static Long getIdOrThrow() {
        return getOrThrow().id();
    }
}
