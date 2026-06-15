package com.qamanager.projectupdate;

import com.qamanager.common.BaseEntity;
import com.qamanager.project.Project;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "project_update")
public class ProjectUpdate extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "version", nullable = false, length = 50)
    private String version;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected ProjectUpdate() {}

    public ProjectUpdate(Project project, String version, String title, String description, UpdateStatus status, int sortOrder) {
        this.project = project;
        this.version = version;
        this.title = title;
        this.description = description;
        this.status = status.getCode();
        this.sortOrder = sortOrder;
    }

    public void update(String version, String title, String description, UpdateStatus status) {
        if (version != null) this.version = version;
        if (title != null) this.title = title;
        if (description != null) this.description = description;
        if (status != null) this.status = status.getCode();
    }

    public void changeSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Long getId() { return id; }
    public Project getProject() { return project; }
    public String getVersion() { return version; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public UpdateStatus getStatus() { return UpdateStatus.from(status); }
    public int getSortOrder() { return sortOrder; }
}
