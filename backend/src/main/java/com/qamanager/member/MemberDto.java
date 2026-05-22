package com.qamanager.member;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class MemberDto {

    public record Response(
        Long id,
        String username,
        String name,
        String role,
        String avatarUrl
    ) {
        public static Response from(TeamMember m) {
            return new Response(m.getId(), m.getUsername(), m.getName(), m.getRole(), m.getAvatarUrl());
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
}
