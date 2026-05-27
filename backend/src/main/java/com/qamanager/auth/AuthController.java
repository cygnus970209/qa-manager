package com.qamanager.auth;

import com.qamanager.common.ApiException;
import com.qamanager.member.MemberDto;
import com.qamanager.member.MemberService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final AuthService authService;
    private final AuthCookieUtil cookieUtil;
    private final TokenBlacklistService blacklist;
    private final MemberService memberService;

    public AuthController(AuthService authService,
                          AuthCookieUtil cookieUtil,
                          TokenBlacklistService blacklist,
                          MemberService memberService) {
        this.authService = authService;
        this.cookieUtil = cookieUtil;
        this.blacklist = blacklist;
        this.memberService = memberService;
    }

    @PostMapping("/auth/login")
    public AuthDto.LoginResponse login(@RequestBody @Valid AuthDto.LoginRequest req,
                                       HttpServletResponse response) {
        AuthDto.IssuedTokens tokens = authService.login(req);
        cookieUtil.writeAuthCookies(response, tokens.accessToken(), tokens.refreshToken());
        return new AuthDto.LoginResponse(tokens.expiresInSeconds(), tokens.user());
    }

    @PostMapping("/auth/refresh")
    public AuthDto.LoginResponse refresh(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = cookieUtil.readRefreshCookie(request);
        if (!StringUtils.hasText(refreshToken)) {
            throw ApiException.unauthorized("리프레시 토큰이 없습니다.");
        }
        // refresh rotation: 새 토큰 발행 후 이전 refresh 는 즉시 무효화
        AuthDto.IssuedTokens tokens = authService.refresh(refreshToken);
        blacklist.blacklist(refreshToken);
        cookieUtil.writeAuthCookies(response, tokens.accessToken(), tokens.refreshToken());
        return new AuthDto.LoginResponse(tokens.expiresInSeconds(), tokens.user());
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        // access/refresh 둘 다 즉시 무효화 → 남은 TTL 동안 재사용 불가
        blacklist.blacklist(cookieUtil.readAccessCookie(request));
        blacklist.blacklist(cookieUtil.readRefreshCookie(request));
        cookieUtil.clearAuthCookies(response);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public AuthDto.MeResponse me() {
        return authService.me(CurrentUser.getIdOrThrow());
    }

    @PatchMapping("/me")
    public AuthDto.MeResponse updateMe(@RequestBody @Valid AuthDto.UpdateMeRequest req) {
        return authService.updateMe(CurrentUser.getIdOrThrow(), req);
    }

    @PostMapping("/me/password")
    public ResponseEntity<Void> changeMyPassword(@RequestBody @Valid AuthDto.ChangeMyPasswordRequest req) {
        authService.changeMyPassword(CurrentUser.getIdOrThrow(), req);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me/notification-settings")
    public MemberDto.NotificationSettings getMyNotificationSettings() {
        return memberService.getNotificationSettings(CurrentUser.getIdOrThrow());
    }

    @PutMapping("/me/notification-settings")
    public MemberDto.NotificationSettings updateMyNotificationSettings(
            @RequestBody @Valid MemberDto.NotificationSettingsRequest req) {
        return memberService.updateNotificationSettings(CurrentUser.getIdOrThrow(), req);
    }
}
