package com.qamanager.member;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class MemberDto {

    public record Response(
        Long id,
        String username,
        String name,
        String email,
        String role,
        String avatarUrl,
        boolean teamsLinked,
        boolean teamsNotifyEnabled
    ) {
        public static Response from(TeamMember m) {
            return new Response(
                m.getId(),
                m.getUsername(),
                m.getName(),
                m.getEmail(),
                m.getRole(),
                m.getAvatarUrl(),
                m.getTeamsUserId() != null,
                m.isTeamsNotifyEnabled()
            );
        }
    }

    public record CreateRequest(
        @NotBlank @Size(min = 3, max = 50) String username,
        @NotBlank @Size(min = 4, max = 100) String password,
        @NotBlank @Size(max = 50) String name,
        @Size(max = 50) String role,
        @Size(max = 500) String avatarUrl
    ) {}

    public record UpdateRequest(
        @Size(max = 50) String name,
        @Size(max = 50) String role,
        @Size(max = 500) String avatarUrl
    ) {}

    /** email 등록/변경 (관리자 또는 본인). 빈 문자열 허용 시 등록 해제. */
    public record EmailRequest(
        @Email @Size(max = 255) String email
    ) {}

    /** Teams 알림 토글. */
    public record TeamsNotifyRequest(
        @NotNull Boolean enabled
    ) {}
}
