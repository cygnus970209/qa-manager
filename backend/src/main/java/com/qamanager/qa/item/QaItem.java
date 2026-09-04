package com.qamanager.qa.item;

import jakarta.persistence.EntityListeners;
import com.qamanager.search.SearchIndexListener;
import com.qamanager.common.BaseEntity;
import com.qamanager.member.TeamMember;
import com.qamanager.projectupdate.ProjectUpdate;
import com.qamanager.qa.shared.QaPriority;
import com.qamanager.qa.shared.QaStatus;
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
@Table(name = "qa_item")
@EntityListeners(SearchIndexListener.class) // 검색 인덱스 갱신 (커밋 후)
public class QaItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "update_id", nullable = false)
    private ProjectUpdate projectUpdate;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "category", length = 50)
    private String category;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 작성자(테스터). 기본은 QA 생성자. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tester_id")
    private TeamMember tester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee1_id")
    private TeamMember assignee1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee2_id")
    private TeamMember assignee2;

    @Column(name = "priority", nullable = false, length = 20)
    private String priority;

    @OneToMany(mappedBy = "qaItem", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private final List<QaItemImage> images = new ArrayList<>();

    protected QaItem() {}

    public QaItem(ProjectUpdate update, String title, String description, String category,
                  QaStatus status, TeamMember tester, TeamMember assignee1, TeamMember assignee2,
                  QaPriority priority) {
        this.projectUpdate = update;
        this.title = title;
        this.description = description;
        this.category = category;
        this.status = status.getCode();
        this.tester = tester;
        this.assignee1 = assignee1;
        this.assignee2 = assignee2;
        this.priority = priority.getCode();
    }

    /** 단일 필드 setter — 변경 감지/이력 기록은 Service 레이어에서 처리 */
    public void setProjectUpdate(ProjectUpdate projectUpdate) { this.projectUpdate = projectUpdate; }
    public void setTitle(String title) { this.title = title; }
    public void setDescription(String description) { this.description = description; }
    public void setCategory(String category) { this.category = category; }
    public void setStatus(QaStatus status) { this.status = status.getCode(); }
    public void setTester(TeamMember tester) { this.tester = tester; }
    public void setAssignee1(TeamMember assignee1) { this.assignee1 = assignee1; }
    public void setAssignee2(TeamMember assignee2) { this.assignee2 = assignee2; }
    public void setPriority(QaPriority priority) { this.priority = priority.getCode(); }

    public void replaceImages(List<String> urls) {
        images.clear();
        if (urls == null) return;
        for (int i = 0; i < urls.size(); i++) {
            images.add(new QaItemImage(this, urls.get(i), i));
        }
    }

    public Long getId() { return id; }
    public ProjectUpdate getProjectUpdate() { return projectUpdate; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }
    public QaStatus getStatus() { return QaStatus.from(status); }
    public TeamMember getTester() { return tester; }
    public TeamMember getAssignee1() { return assignee1; }
    public TeamMember getAssignee2() { return assignee2; }
    public QaPriority getPriority() { return QaPriority.from(priority); }
    public List<QaItemImage> getImages() { return Collections.unmodifiableList(images); }
}
