package com.qamanager.audit;

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
 * API 요청 감사 로그. 변경(POST/PUT/PATCH/DELETE) 또는 /api/auth/* 경로만 적재된다.
 * member_id 는 FK 두지 않는다 (멤버 삭제와 무관하게 보존).
 */
@Entity
@Table(name = "api_request_log")
@EntityListeners(AuditingEntityListener.class)
public class ApiRequestLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "method", nullable = false, length = 10)
    private String method;

    @Column(name = "path", nullable = false, length = 500)
    private String path;

    @Column(name = "query_string", length = 1000)
    private String queryString;

    @Column(name = "status")
    private Integer status;

    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "username", length = 50)
    private String username;

    @Column(name = "ip", length = 64)
    private String ip;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected ApiRequestLog() {}

    public ApiRequestLog(String method, String path, String queryString, Integer status,
                         Long memberId, String username, String ip, String userAgent,
                         Integer durationMs, String errorMessage) {
        this.method = method;
        this.path = truncate(path, 500);
        this.queryString = truncate(queryString, 1000);
        this.status = status;
        this.memberId = memberId;
        this.username = truncate(username, 50);
        this.ip = truncate(ip, 64);
        this.userAgent = truncate(userAgent, 500);
        this.durationMs = durationMs;
        this.errorMessage = truncate(errorMessage, 500);
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }

    public Long getId() { return id; }
    public String getMethod() { return method; }
    public String getPath() { return path; }
    public String getQueryString() { return queryString; }
    public Integer getStatus() { return status; }
    public Long getMemberId() { return memberId; }
    public String getUsername() { return username; }
    public String getIp() { return ip; }
    public String getUserAgent() { return userAgent; }
    public Integer getDurationMs() { return durationMs; }
    public String getErrorMessage() { return errorMessage; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
