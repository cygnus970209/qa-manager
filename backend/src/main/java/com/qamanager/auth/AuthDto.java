package com.qamanager.auth;

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
