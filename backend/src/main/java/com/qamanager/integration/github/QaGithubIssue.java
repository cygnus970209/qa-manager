package com.qamanager.integration.github;

import com.qamanager.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * QA 항목 ↔ GitHub 이슈 매핑.
 * repo 정보를 매핑에도 보관한다 — 프로젝트의 연결 repo 가 이후 바뀌어도 기존 이슈 추적을 유지하기 위함.
 */
@Entity
@Table(name = "qa_github_issue")
public class QaGithubIssue extends BaseEntity {

    public static final String STATE_OPEN = "open";
    public static final String STATE_CLOSED = "closed";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "qa_item_id", nullable = false)
    private Long qaItemId;

    @Column(name = "repo_owner", nullable = false, length = 100)
    private String repoOwner;

    @Column(name = "repo_name", nullable = false, length = 200)
    private String repoName;

    @Column(name = "issue_number", nullable = false)
    private Integer issueNumber;

    @Column(name = "issue_url", nullable = false, length = 500)
    private String issueUrl;

    @Column(name = "state", nullable = false, length = 20)
    private String state;

    protected QaGithubIssue() {}

    public QaGithubIssue(Long qaItemId, String repoOwner, String repoName,
                         Integer issueNumber, String issueUrl, String state) {
        this.qaItemId = qaItemId;
        this.repoOwner = repoOwner;
        this.repoName = repoName;
        this.issueNumber = issueNumber;
        this.issueUrl = issueUrl;
        this.state = state;
    }

    public void updateState(String state) {
        this.state = state;
    }

    public Long getId() { return id; }
    public Long getQaItemId() { return qaItemId; }
    public String getRepoOwner() { return repoOwner; }
    public String getRepoName() { return repoName; }
    public Integer getIssueNumber() { return issueNumber; }
    public String getIssueUrl() { return issueUrl; }
    public String getState() { return state; }
}
