package com.qamanager.project;

import com.qamanager.member.TeamMember;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "project_pin")
@IdClass(ProjectPinId.class)
@EntityListeners(AuditingEntityListener.class)
public class ProjectPin {

    @jakarta.persistence.Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    @jakarta.persistence.Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private TeamMember member;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected ProjectPin() {}

    public ProjectPin(Project project, TeamMember member) {
        this.project = project;
        this.member = member;
    }

    public Project getProject() { return project; }
    public TeamMember getMember() { return member; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
