package com.qamanager.auth;

import com.qamanager.member.AccountRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AuthDto {

    public record LoginRequest(
        @NotBlank String username,
        @NotBlank String password
    ) {}

    /** 클라이언트 응답. 토큰은 HttpOnly 쿠키로만 발행됨. */
    public record LoginResponse(
        long expiresInSeconds,
        MeResponse user
    ) {}

    /**
     * 로그인/OTP 검증 통합 응답. (non_null inclusion 으로 빈 필드는 직렬화에서 제외)
     * - 인증 완료: authenticated=true, expiresInSeconds, user
     * - OTP 필요: authenticated=false, otpRequired=true, challengeId, maskedEmail, otpExpiresInSeconds
     */
    public record AuthResponse(
        boolean authenticated,
        Boolean otpRequired,
        Long expiresInSeconds,
        MeResponse user,
        String challengeId,
        String maskedEmail,
        Long otpExpiresInSeconds
    ) {
        public static AuthResponse authenticated(long expiresInSeconds, MeResponse user) {
            return new AuthResponse(true, null, expiresInSeconds, user, null, null, null);
        }
        public static AuthResponse otp(String challengeId, String maskedEmail, long otpExpiresInSeconds) {
            return new AuthResponse(false, true, null, null, challengeId, maskedEmail, otpExpiresInSeconds);
        }
    }

    /** AuthService.login 내부 분기 결과. */
    public record LoginResult(boolean otpRequired, IssuedTokens tokens, OtpChallenge challenge) {
        public static LoginResult authenticated(IssuedTokens tokens) {
            return new LoginResult(false, tokens, null);
        }
        public static LoginResult otp(OtpChallenge challenge) {
            return new LoginResult(true, null, challenge);
        }
    }

    public record OtpChallenge(String challengeId, String maskedEmail, long expiresInSeconds) {}

    public record OtpVerifyRequest(
        @NotBlank String challengeId,
        @NotBlank String code
    ) {}

    public record OtpResendRequest(
        @NotBlank String challengeId
    ) {}

    /** 인증 서비스 내부에서 토큰을 들고 다니기 위한 record. */
    public record IssuedTokens(
        String accessToken,
        String refreshToken,
        long expiresInSeconds,
        MeResponse user
    ) {}

    public record MeResponse(
        Long id,
        String username,
        String name,
        String role,
        AccountRole accountRole,
        String avatarUrl
    ) {}

    public record UpdateMeRequest(
        @Size(max = 50) String name,
        @Size(max = 500) String avatarUrl
    ) {}

    public record ChangeMyPasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank @Size(min = 4, max = 100) String newPassword
    ) {}
}
