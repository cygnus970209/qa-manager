package com.qamanager.auth;

import com.qamanager.auth.otp.LoginOtpService;
import com.qamanager.auth.otp.SecurityOtpProperties;
import com.qamanager.auth.otp.TrustedNetworkChecker;
import com.qamanager.common.ApiException;
import com.qamanager.member.TeamMember;
import com.qamanager.member.TeamMemberRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final TeamMemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final TokenBlacklistService blacklist;
    private final SecurityOtpProperties otpProps;
    private final TrustedNetworkChecker trustedNetworkChecker;
    private final LoginOtpService otpService;

    public AuthService(TeamMemberRepository memberRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider tokenProvider,
                       TokenBlacklistService blacklist,
                       SecurityOtpProperties otpProps,
                       TrustedNetworkChecker trustedNetworkChecker,
                       LoginOtpService otpService) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.blacklist = blacklist;
        this.otpProps = otpProps;
        this.trustedNetworkChecker = trustedNetworkChecker;
        this.otpService = otpService;
    }

    /**
     * 1단계 로그인. 자격 검증 후 IP 에 따라 분기.
     * - OTP 비활성 또는 신뢰 IP → 즉시 토큰 발급
     * - 그 외 → 이메일 OTP 발급(이메일 미등록자는 차단)
     */
    @Transactional(readOnly = true)
    public AuthDto.LoginResult login(AuthDto.LoginRequest req, String clientIp) {
        TeamMember m = memberRepository.findByUsernameAndDeletedAtIsNull(req.username())
            .orElseThrow(() -> ApiException.unauthorized("아이디 또는 비밀번호가 일치하지 않습니다."));

        if (!passwordEncoder.matches(req.password(), m.getPasswordHash())) {
            throw ApiException.unauthorized("아이디 또는 비밀번호가 일치하지 않습니다.");
        }

        if (!otpProps.isEnabled() || trustedNetworkChecker.isTrusted(clientIp)) {
            return AuthDto.LoginResult.authenticated(issueTokens(m));
        }

        // 신뢰 IP 밖 → 이메일 OTP. email 우선, 없으면 username 이 이메일 형식이면 그것을 사용.
        // 둘 다 없으면 차단(설계 4.7-A).
        String email = m.resolveEmailCandidate();
        if (email == null || email.isBlank()) {
            throw ApiException.forbidden("이메일이 등록되지 않아 인증 코드를 보낼 수 없습니다. 관리자에게 이메일 등록을 요청하세요.");
        }
        LoginOtpService.Challenge ch = otpService.issue(m, email);
        return AuthDto.LoginResult.otp(
            new AuthDto.OtpChallenge(ch.challengeId(), ch.maskedEmail(), ch.expiresInSeconds()));
    }

    /** 2단계: OTP 검증 성공 시 토큰 발급. 실패는 사유별 예외. */
    @Transactional(readOnly = true)
    public AuthDto.IssuedTokens verifyOtp(String challengeId, String code) {
        LoginOtpService.VerifyResult r = otpService.verify(challengeId, code);
        if (!r.success()) {
            switch (r.error()) {
                case "expired" -> throw ApiException.unauthorized("인증 세션이 만료되었습니다. 다시 로그인해 주세요.");
                case "too_many_attempts" -> throw ApiException.unauthorized("인증 시도 횟수를 초과했습니다. 다시 로그인해 주세요.");
                default -> throw ApiException.otpInvalid("인증 코드가 올바르지 않습니다.", r.remainingAttempts());
            }
        }
        TeamMember m = memberRepository.findByIdAndDeletedAtIsNull(r.memberId())
            .orElseThrow(() -> ApiException.unauthorized("멤버가 존재하지 않습니다."));
        return issueTokens(m);
    }

    /** OTP 재발송(쿨다운 적용). */
    public AuthDto.OtpChallenge resendOtp(String challengeId) {
        LoginOtpService.Challenge ch = otpService.resend(challengeId);
        return new AuthDto.OtpChallenge(ch.challengeId(), ch.maskedEmail(), ch.expiresInSeconds());
    }

    @Transactional(readOnly = true)
    public AuthDto.IssuedTokens refresh(String refreshToken) {
        Claims claims;
        try {
            claims = tokenProvider.parse(refreshToken);
        } catch (JwtException e) {
            throw ApiException.unauthorized("리프레시 토큰이 유효하지 않습니다.");
        }
        if (!tokenProvider.isRefresh(claims)) {
            throw ApiException.unauthorized("리프레시 토큰이 아닙니다.");
        }
        // 이미 사용된(블랙리스트 등록된) refresh 는 재사용 불가 (토큰 도용 방어)
        if (blacklist.isBlacklisted(tokenProvider.getJti(claims))) {
            throw ApiException.unauthorized("이미 사용된 리프레시 토큰입니다.");
        }
        Long memberId = tokenProvider.getMemberId(claims);
        TeamMember m = memberRepository.findByIdAndDeletedAtIsNull(memberId)
            .orElseThrow(() -> ApiException.unauthorized("멤버가 존재하지 않습니다."));
        return issueTokens(m);
    }

    @Transactional(readOnly = true)
    public AuthDto.MeResponse me(Long memberId) {
        TeamMember m = memberRepository.findByIdAndDeletedAtIsNull(memberId)
            .orElseThrow(() -> ApiException.unauthorized("멤버가 존재하지 않습니다."));
        return new AuthDto.MeResponse(m.getId(), m.getUsername(), m.getName(), m.getRole(), m.getAvatarUrl());
    }

    @Transactional
    public AuthDto.MeResponse updateMe(Long memberId, AuthDto.UpdateMeRequest req) {
        TeamMember m = memberRepository.findByIdAndDeletedAtIsNull(memberId)
            .orElseThrow(() -> ApiException.unauthorized("멤버가 존재하지 않습니다."));
        // 본인은 name, avatarUrl 만 수정 가능 (role 은 관리자 권한)
        m.update(req.name(), null, req.avatarUrl());
        return new AuthDto.MeResponse(m.getId(), m.getUsername(), m.getName(), m.getRole(), m.getAvatarUrl());
    }

    @Transactional
    public void changeMyPassword(Long memberId, AuthDto.ChangeMyPasswordRequest req) {
        TeamMember m = memberRepository.findByIdAndDeletedAtIsNull(memberId)
            .orElseThrow(() -> ApiException.unauthorized("멤버가 존재하지 않습니다."));
        if (!passwordEncoder.matches(req.currentPassword(), m.getPasswordHash())) {
            throw ApiException.unauthorized("현재 비밀번호가 일치하지 않습니다.");
        }
        m.changePassword(passwordEncoder.encode(req.newPassword()));
    }

    private AuthDto.IssuedTokens issueTokens(TeamMember m) {
        String access = tokenProvider.createAccessToken(m.getId(), m.getUsername());
        String refresh = tokenProvider.createRefreshToken(m.getId(), m.getUsername());
        AuthDto.MeResponse user = new AuthDto.MeResponse(
            m.getId(), m.getUsername(), m.getName(), m.getRole(), m.getAvatarUrl()
        );
        return new AuthDto.IssuedTokens(access, refresh, tokenProvider.getAccessTtl().toSeconds(), user);
    }
}
