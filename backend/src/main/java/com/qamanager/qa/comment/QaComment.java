package com.qamanager.qa.comment;

import jakarta.persistence.EntityListeners;
import com.qamanager.search.SearchIndexListener;
import com.qamanager.common.BaseEntity;
import com.qamanager.member.TeamMember;
import com.qamanager.qa.item.QaItem;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "qa_comment")
@EntityListeners(SearchIndexListener.class) // 검색 인덱스 갱신 (커밋 후)
public class QaComment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "qa_item_id", nullable = false)
    private QaItem qaItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private QaComment parent;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private TeamMember author;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @OneToMany(mappedBy = "comment", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private final List<QaCommentImage> images = new ArrayList<>();

    protected QaComment() {}

    public QaComment(QaItem qaItem, QaComment parent, TeamMember author, String content) {
        this.qaItem = qaItem;
        this.parent = parent;
        this.author = author;
        this.content = content;
    }

    public void setContent(String content) { this.content = content; }

    public void replaceImages(List<String> urls) {
        images.clear();
        if (urls == null) return;
        for (int i = 0; i < urls.size(); i++) {
            images.add(new QaCommentImage(this, urls.get(i), i));
        }
    }

    public Long getId() { return id; }
    public QaItem getQaItem() { return qaItem; }
    public QaComment getParent() { return parent; }
    public TeamMember getAuthor() { return author; }
    public String getContent() { return content; }
    public List<QaCommentImage> getImages() { return Collections.unmodifiableList(images); }
}
