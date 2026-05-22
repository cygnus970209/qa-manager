package com.qamanager.notification;

import com.qamanager.member.TeamMember;
import com.qamanager.project.Project;
import com.qamanager.qa.item.QaItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "notification")
@EntityListeners(AuditingEntityListener.class)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false)
    private TeamMember recipient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private TeamMember actor;

    @Column(name = "type", nullable = false, length = 20)
    private String type;

    @Column(name = "message", nullable = false, length = 500)
    private String message;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "qa_item_id")
    private QaItem qaItem;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Notification() {}

    public Notification(TeamMember recipient, TeamMember actor, String type, String message,
                        Project project, QaItem qaItem) {
        this.recipient = recipient;
        this.actor = actor;
        this.type = type;
        this.message = message;
        this.project = project;
        this.qaItem = qaItem;
        this.read = false;
    }

    public void markRead() { this.read = true; }

    public Long getId() { return id; }
    public TeamMember getRecipient() { return recipient; }
    public TeamMember getActor() { return actor; }
    public String getType() { return type; }
    public String getMessage() { return message; }
    public Project getProject() { return project; }
    public QaItem getQaItem() { return qaItem; }
    public boolean isRead() { return read; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
