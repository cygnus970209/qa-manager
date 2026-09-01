package com.qamanager.member;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class MemberDto {

    public record Response(
        Long id,
        String username,
        String name,
        String email,
        String role,
        AccountRole accountRole,
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
                m.getAccountRole(),
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

    /** 계정 권한 변경 (관리자 전용). */
    public record AccountRoleRequest(
        @NotNull AccountRole accountRole
    ) {}

    /** 본인 알림 개인화 설정 (Teams 발송에만 적용). */
    public record NotificationSettings(
        boolean teamsNotifyEnabled,
        boolean notifyQaEnabled,
        boolean notifyCommentEnabled,
        boolean notifyReplyEnabled,
        String quietHoursStart,
        String quietHoursEnd
    ) {
        public static NotificationSettings from(TeamMember m) {
            return new NotificationSettings(
                m.isTeamsNotifyEnabled(),
                m.isNotifyQaEnabled(),
                m.isNotifyCommentEnabled(),
                m.isNotifyReplyEnabled(),
                m.getQuietHoursStart(),
                m.getQuietHoursEnd()
            );
        }
    }

    private static final String HHMM = "^([01]?\\d|2[0-3]):[0-5]\\d$";

    public record NotificationSettingsRequest(
        @NotNull Boolean teamsNotifyEnabled,
        @NotNull Boolean notifyQaEnabled,
        @NotNull Boolean notifyCommentEnabled,
        @NotNull Boolean notifyReplyEnabled,
        @Pattern(regexp = HHMM, message = "시간 형식은 HH:mm 이어야 합니다.") String quietHoursStart,
        @Pattern(regexp = HHMM, message = "시간 형식은 HH:mm 이어야 합니다.") String quietHoursEnd
    ) {}
}
