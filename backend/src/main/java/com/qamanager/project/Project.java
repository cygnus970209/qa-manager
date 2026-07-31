package com.qamanager.project;

import com.qamanager.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "project")
public class Project extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    // ── GitHub 연동 (선택) — 연결된 repo 로 QA 이슈를 생성한다 ──
    @Column(name = "github_installation_id")
    private Long githubInstallationId;

    @Column(name = "github_repo_owner", length = 100)
    private String githubRepoOwner;

    @Column(name = "github_repo_name", length = 200)
    private String githubRepoName;

    protected Project() {}

    public Project(String name, String description, ProjectStatus status) {
        this.name = name;
        this.description = description;
        this.status = status.getCode();
    }

    public void update(String name, String description, ProjectStatus status) {
        if (name != null) this.name = name;
        if (description != null) this.description = description;
        if (status != null) this.status = status.getCode();
    }

    public void connectGithubRepo(Long installationId, String owner, String name) {
        this.githubInstallationId = installationId;
        this.githubRepoOwner = owner;
        this.githubRepoName = name;
    }

    public void clearGithubRepo() {
        this.githubInstallationId = null;
        this.githubRepoOwner = null;
        this.githubRepoName = null;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public ProjectStatus getStatus() { return ProjectStatus.from(status); }
    public Long getGithubInstallationId() { return githubInstallationId; }
    public String getGithubRepoOwner() { return githubRepoOwner; }
    public String getGithubRepoName() { return githubRepoName; }
}
