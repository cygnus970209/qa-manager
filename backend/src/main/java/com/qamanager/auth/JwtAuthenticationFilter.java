package com.qamanager.auth;

import com.qamanager.member.TeamMemberRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;
    private final TeamMemberRepository memberRepository;
    private final AuthCookieUtil cookieUtil;
    private final TokenBlacklistService blacklist;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider,
                                   TeamMemberRepository memberRepository,
                                   AuthCookieUtil cookieUtil,
                                   TokenBlacklistService blacklist) {
        this.tokenProvider = tokenProvider;
        this.memberRepository = memberRepository;
        this.cookieUtil = cookieUtil;
        this.blacklist = blacklist;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (StringUtils.hasText(token)) {
            try {
                Claims claims = tokenProvider.parse(token);
                String jti = tokenProvider.getJti(claims);
                if (tokenProvider.isAccess(claims) && !blacklist.isBlacklisted(jti)) {
                    Long memberId = tokenProvider.getMemberId(claims);
                    memberRepository.findById(memberId)
                        .map(AuthPrincipal::from)
                        .ifPresent(principal -> {
                            UsernamePasswordAuthenticationToken auth =
                                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                            SecurityContextHolder.getContext().setAuthentication(auth);
                        });
                }
            } catch (JwtException ignored) {
                // 잘못된 토큰은 인증되지 않은 상태로 통과 (이후 SecurityFilterChain이 401 처리)
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        // 1순위: Authorization 헤더 (외부 API 호환)
        String header = request.getHeader(HEADER);
        if (StringUtils.hasText(header) && header.startsWith(PREFIX)) {
            return header.substring(PREFIX.length());
        }
        // 2순위: HttpOnly 쿠키 (브라우저 SPA)
        String cookieToken = cookieUtil.readAccessCookie(request);
        return StringUtils.hasText(cookieToken) ? cookieToken : null;
    }
}
