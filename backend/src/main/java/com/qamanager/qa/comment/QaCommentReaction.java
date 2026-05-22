package com.qamanager.qa.comment;

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
@Table(name = "qa_comment_reaction")
@IdClass(QaCommentReactionId.class)
@EntityListeners(AuditingEntityListener.class)
public class QaCommentReaction {

    @jakarta.persistence.Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comment_id")
    private QaComment comment;

    @jakarta.persistence.Id
    @Column(name = "emoji", length = 20)
    private String emoji;

    @jakarta.persistence.Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private TeamMember member;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected QaCommentReaction() {}

    public QaCommentReaction(QaComment comment, String emoji, TeamMember member) {
        this.comment = comment;
        this.emoji = emoji;
        this.member = member;
    }

    public QaComment getComment() { return comment; }
    public String getEmoji() { return emoji; }
    public TeamMember getMember() { return member; }
}
