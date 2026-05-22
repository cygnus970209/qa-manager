package com.qamanager.qa.comment;

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
@Table(name = "qa_comment_image")
@EntityListeners(AuditingEntityListener.class)
public class QaCommentImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comment_id", nullable = false)
    private QaComment comment;

    @Column(name = "image_url", nullable = false, length = 800)
    private String imageUrl;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected QaCommentImage() {}

    public QaCommentImage(QaComment comment, String imageUrl, int sortOrder) {
        this.comment = comment;
        this.imageUrl = imageUrl;
        this.sortOrder = sortOrder;
    }

    public Long getId() { return id; }
    public String getImageUrl() { return imageUrl; }
    public int getSortOrder() { return sortOrder; }
}
