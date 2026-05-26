package com.qamanager.notification.teams;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Teams 메세지 발송 시도/결과 로그.
 * status 는 SENT / SKIPPED / FAILED 셋 중 하나.
 */
@Entity
@Table(name = "teams_send_log")
@EntityListeners(AuditingEntityListener.class)
public class TeamsSendLog {

    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_SKIPPED = "SKIPPED";
    public static final String STATUS_FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "notification_type", length = 20)
    private String notificationType;

    @Column(name = "recipient_id")
    private Long recipientId;

    @Column(name = "recipient_email", length = 255)
    private String recipientEmail;

    @Column(name = "message", length = 500)
    private String message;

    @Column(name = "detail", length = 1000)
    private String detail;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected TeamsSendLog() {}

    public TeamsSendLog(String status, String notificationType, Long recipientId,
                        String recipientEmail, String message, String detail, Integer durationMs) {
        this.status = status;
        this.notificationType = notificationType;
        this.recipientId = recipientId;
        this.recipientEmail = recipientEmail;
        this.message = truncate(message, 500);
        this.detail = truncate(detail, 1000);
        this.durationMs = durationMs;
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }

    public Long getId() { return id; }
    public String getStatus() { return status; }
    public String getNotificationType() { return notificationType; }
    public Long getRecipientId() { return recipientId; }
    public String getRecipientEmail() { return recipientEmail; }
    public String getMessage() { return message; }
    public String getDetail() { return detail; }
    public Integer getDurationMs() { return durationMs; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
