package com.qamanager.member;

import com.qamanager.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    /** 계정 권한. 직무 표기(role)와 별개의 접근 제어 개념. 신규 멤버 기본 MEMBER. */
    @Enumerated(EnumType.STRING)
    @Column(name = "account_role", nullable = false, length = 20)
    private AccountRole accountRole = AccountRole.MEMBER;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(name = "teams_user_id", length = 64)
    private String teamsUserId;

    /** Bot Framework conversation id (봇과의 1:1). 최초 발송 또는 설치 이벤트 시 캐시.
     *  Bot Framework conversation id 는 길어서(개인 1:1 의 a: 형식은 150자+) 512 로 둔다. */
    @Column(name = "teams_chat_id", length = 512)
    private String teamsChatId;

    /** Bot Connector serviceUrl. 설치 이벤트에서 캐시. 없으면 글로벌 기본값으로 발송. */
    @Column(name = "teams_service_url", length = 255)
    private String teamsServiceUrl;

    /** Teams 알림 전체 on/off (마스터 스위치). */
    @Column(name = "teams_notify_enabled", nullable = false)
    private boolean teamsNotifyEnabled = true;

    /** 알림 종류별 on/off (Teams 발송에만 적용). type: qa / comment / reply. */
    @Column(name = "notify_qa_enabled", nullable = false)
    private boolean notifyQaEnabled = true;

    @Column(name = "notify_comment_enabled", nullable = false)
    private boolean notifyCommentEnabled = true;

    @Column(name = "notify_reply_enabled", nullable = false)
    private boolean notifyReplyEnabled = true;

    /** 방해금지 시간대 "HH:mm" (서버 Asia/Seoul 기준). null 이면 미설정. 구간 내 Teams 발송은 스킵. */
    @Column(name = "quiet_hours_start", length = 5)
    private String quietHoursStart;

    @Column(name = "quiet_hours_end", length = 5)
    private String quietHoursEnd;

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

    public void changeAccountRole(AccountRole accountRole) {
        this.accountRole = accountRole;
    }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }

    /** email 등록/변경. 변경되면 캐시된 AAD/conversation 정보는 무효화. */
    public void updateEmail(String email) {
        if (email != null && !email.equals(this.email)) {
            this.email = email;
            this.teamsUserId = null;
            this.teamsChatId = null;
            this.teamsServiceUrl = null;
        }
    }

    /** Teams 발송 가능 상태 캐시. AAD 조회 결과를 저장한다. */
    public void linkTeamsUser(String teamsUserId) {
        this.teamsUserId = teamsUserId;
        this.teamsChatId = null;
        this.teamsServiceUrl = null;
    }

    /** Bot Framework conversation 캐시. conversation id 와 serviceUrl 을 함께 저장한다. */
    public void cacheConversation(String conversationId, String serviceUrl) {
        this.teamsChatId = conversationId;
        if (serviceUrl != null && !serviceUrl.isBlank()) {
            this.teamsServiceUrl = serviceUrl;
        }
    }

    public void setTeamsNotifyEnabled(boolean enabled) {
        this.teamsNotifyEnabled = enabled;
    }

    /** 알림 개인화 설정 일괄 갱신 (본인 설정 화면에서 호출). */
    public void updateNotificationSettings(boolean teamsEnabled, boolean qa, boolean comment, boolean reply,
                                           String quietStart, String quietEnd) {
        this.teamsNotifyEnabled = teamsEnabled;
        this.notifyQaEnabled = qa;
        this.notifyCommentEnabled = comment;
        this.notifyReplyEnabled = reply;
        this.quietHoursStart = quietStart;
        this.quietHoursEnd = quietEnd;
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
    public AccountRole getAccountRole() { return accountRole; }
    public boolean isAdmin() { return accountRole == AccountRole.ADMIN; }
    public String getAvatarUrl() { return avatarUrl; }
    public String getTeamsUserId() { return teamsUserId; }
    public String getTeamsChatId() { return teamsChatId; }
    public String getTeamsServiceUrl() { return teamsServiceUrl; }
    public boolean isTeamsNotifyEnabled() { return teamsNotifyEnabled; }
    public boolean isNotifyQaEnabled() { return notifyQaEnabled; }
    public boolean isNotifyCommentEnabled() { return notifyCommentEnabled; }
    public boolean isNotifyReplyEnabled() { return notifyReplyEnabled; }
    public String getQuietHoursStart() { return quietHoursStart; }
    public String getQuietHoursEnd() { return quietHoursEnd; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
}
