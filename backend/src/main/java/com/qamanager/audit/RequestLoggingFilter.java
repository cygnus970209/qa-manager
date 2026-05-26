package com.qamanager.audit;

import com.qamanager.auth.AuthPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * API 요청 감사 로그 filter.
 *
 * 적재 대상:
 *  - 변경 요청 (POST/PUT/PATCH/DELETE)
 *  - /api/auth/* 경로의 모든 메서드 (로그인/리프레시/로그아웃)
 *
 * 제외:
 *  - /actuator/**, /swagger-ui/**, /v3/api-docs/**, /error
 *
 * filter 순서는 JwtAuthenticationFilter 보다 뒤 (Spring Security 가 인증 처리 후 SecurityContext 에 principal 이 채워진 상태에서 캡처).
 * 본 filter 는 SecurityFilterChain 이후로 동작하도록 일반 Component 로 등록 + @Order 후순위.
 */
@Component
@Order(Integer.MAX_VALUE - 100)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private final ApiRequestLogService logService;

    public RequestLoggingFilter(ApiRequestLogService logService) {
        this.logService = logService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        if (!shouldLog(request)) {
            chain.doFilter(request, response);
            return;
        }

        long start = System.currentTimeMillis();
        String errorMessage = null;
        try {
            chain.doFilter(request, response);
        } catch (RuntimeException | IOException | ServletException e) {
            errorMessage = e.getClass().getSimpleName() + ": " + e.getMessage();
            throw e;
        } finally {
            int duration = (int) (System.currentTimeMillis() - start);
            Long memberId = null;
            String username = null;
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof AuthPrincipal p) {
                memberId = p.id();
                username = p.username();
            }
            logService.record(
                request.getMethod(),
                request.getRequestURI(),
                request.getQueryString(),
                response.getStatus(),
                memberId,
                username,
                clientIp(request),
                request.getHeader("User-Agent"),
                duration,
                errorMessage
            );
        }
    }

    private boolean shouldLog(HttpServletRequest req) {
        String path = req.getRequestURI();
        if (path == null) return false;
        if (path.startsWith("/actuator")
            || path.startsWith("/swagger-ui")
            || path.startsWith("/v3/api-docs")
            || path.equals("/error")
            || path.equals("/api/ping")
            || path.equals("/favicon.ico")) {
            return false;
        }
        String method = req.getMethod();
        if (path.startsWith("/api/auth/")) return true;
        return !"GET".equalsIgnoreCase(method)
            && !"OPTIONS".equalsIgnoreCase(method)
            && !"HEAD".equalsIgnoreCase(method);
    }

    /** X-Forwarded-For 우선 (CDN/proxy 환경). 콤마로 여러 개면 첫 번째. */
    private String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        return req.getRemoteAddr();
    }
}
