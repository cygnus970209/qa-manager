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

    @Column(name = "role", length = 50)
    private String role;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

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

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getName() { return name; }
    public String getRole() { return role; }
    public String getAvatarUrl() { return avatarUrl; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
}
