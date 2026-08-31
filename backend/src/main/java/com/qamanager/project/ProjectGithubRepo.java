package com.qamanager.project;

import com.qamanager.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 프로젝트에 연결된 GitHub repo (1:N). QA 이슈는 이 목록 중 하나의 repo 에 생성된다. */
@Entity
@Table(name = "project_github_repo")
public class ProjectGithubRepo extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "installation_id", nullable = false)
    private Long installationId;

    @Column(name = "repo_owner", nullable = false, length = 100)
    private String repoOwner;

    @Column(name = "repo_name", nullable = false, length = 200)
    private String repoName;

    protected ProjectGithubRepo() {}

    public ProjectGithubRepo(Long projectId, Long installationId, String repoOwner, String repoName) {
        this.projectId = projectId;
        this.installationId = installationId;
        this.repoOwner = repoOwner;
        this.repoName = repoName;
    }

    public Long getId() { return id; }
    public Long getProjectId() { return projectId; }
    public Long getInstallationId() { return installationId; }
    public String getRepoOwner() { return repoOwner; }
    public String getRepoName() { return repoName; }
}
