package com.qamanager.auth;

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

    public AuthService(TeamMemberRepository memberRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider tokenProvider,
                       TokenBlacklistService blacklist) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.blacklist = blacklist;
    }

    @Transactional(readOnly = true)
    public AuthDto.IssuedTokens login(AuthDto.LoginRequest req) {
        TeamMember m = memberRepository.findByUsernameAndDeletedAtIsNull(req.username())
            .orElseThrow(() -> ApiException.unauthorized("아이디 또는 비밀번호가 일치하지 않습니다."));

        if (!passwordEncoder.matches(req.password(), m.getPasswordHash())) {
            throw ApiException.unauthorized("아이디 또는 비밀번호가 일치하지 않습니다.");
        }
        return issueTokens(m);
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
