package com.qamanager.member;

import com.qamanager.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 소프트 삭제 정책:
 * - deleted_at IS NULL 인 row 만 활성 멤버.
 * - 엔티티 레벨 SQLRestriction 은 두지 않는다 (ManyToOne 참조의 lazy 초기화까지 막아
 *   historical 데이터인 댓글/QA assignee 등이 깨지기 때문).
 * - 로그인/인증/멤버 목록은 Repository 의 active 메서드(`findByIdAndDeletedAtIsNull` 등)로 필터링.
 */
@Entity
@Table(name = "team_member")
public class TeamMember extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "email", length = 255, unique = true)
    private String email;

    @Column(name = "role", length = 50)
    private String role;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(name = "teams_user_id", length = 64)
    private String teamsUserId;

    @Column(name = "teams_chat_id", length = 128)
    private String teamsChatId;

    @Column(name = "teams_notify_enabled", nullable = false)
    private boolean teamsNotifyEnabled = true;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    protected TeamMember() {}

    public TeamMember(String username, String passwordHash, String name, String role, String avatarUrl) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.name = name;
        this.role = role;
        this.avatarUrl = avatarUrl;
    }

    public void update(String name, String role, String avatarUrl) {
        if (name != null) this.name = name;
        if (role != null) this.role = role;
        if (avatarUrl != null) this.avatarUrl = avatarUrl;
    }

    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
    }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }

    /** email 등록/변경. 변경되면 캐시된 AAD/chat id 는 무효화. */
    public void updateEmail(String email) {
        if (email != null && !email.equals(this.email)) {
            this.email = email;
            this.teamsUserId = null;
            this.teamsChatId = null;
        }
    }

    /** Teams 발송 가능 상태 캐시. AAD 조회 결과를 저장한다. */
    public void linkTeamsUser(String teamsUserId) {
        this.teamsUserId = teamsUserId;
        this.teamsChatId = null;
    }

    public void cacheTeamsChat(String chatId) {
        this.teamsChatId = chatId;
    }

    public void setTeamsNotifyEnabled(boolean enabled) {
        this.teamsNotifyEnabled = enabled;
    }

    /**
     * Teams 발송에 사용할 email 후보.
     * 1) 명시적으로 등록된 email 이 있으면 우선
     * 2) 없으면 username 이 회사 이메일로 세팅된 운영 환경 가정 → username 반환
     * 도메인 일치 검증은 호출자가 TeamsProperties#matchesEmailDomain 으로 수행한다.
     */
    public String resolveEmailCandidate() {
        if (email != null && !email.isBlank()) return email;
        if (username != null && username.contains("@")) return username;
        return null;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getRole() { return role; }
    public String getAvatarUrl() { return avatarUrl; }
    public String getTeamsUserId() { return teamsUserId; }
    public String getTeamsChatId() { return teamsChatId; }
    public boolean isTeamsNotifyEnabled() { return teamsNotifyEnabled; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
}
