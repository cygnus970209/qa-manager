package com.qamanager.search;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** 통합 검색 인덱스 한 행 = 검색 대상 하나 (V20). 원본이 바뀌면 SearchIndexService 가 통째로 다시 쓴다. */
@Entity
@Table(name = "search_document")
public class SearchDocument {

    public static final String TYPE_QA = "qa";
    public static final String TYPE_COMMENT = "comment";
    public static final String TYPE_PROJECT = "project";
    public static final String TYPE_UPDATE = "update";
    public static final String TYPE_TEST_CASE = "test_case";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entity_type", nullable = false, length = 20)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "update_id")
    private Long updateId;

    @Column(name = "qa_item_id")
    private Long qaItemId;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "body", columnDefinition = "TEXT")
    private String body;

    @Column(name = "search_text", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String searchText;

    @Column(name = "ngram_text", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String ngramText;

    @Column(name = "status", length = 30)
    private String status;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected SearchDocument() {}

    public SearchDocument(String entityType, Long entityId) {
        this.entityType = entityType;
        this.entityId = entityId;
    }

    /** 내용을 채운다 (신규·갱신 공통). 검색용 열은 제목+본문에서 파생. */
    public void fill(Long projectId, Long updateId, Long qaItemId, String title, String body, String status) {
        this.projectId = projectId;
        this.updateId = updateId;
        this.qaItemId = qaItemId;
        this.title = SearchTokenizer.clip(title == null || title.isBlank() ? "(제목 없음)" : title, 500);
        this.body = SearchTokenizer.clip(body, 20_000);
        String joined = (this.title + "\n" + (this.body == null ? "" : this.body));
        this.searchText = SearchTokenizer.normalize(joined);
        this.ngramText = SearchTokenizer.indexText(joined);
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getEntityType() { return entityType; }
    public Long getEntityId() { return entityId; }
    public Long getProjectId() { return projectId; }
    public Long getUpdateId() { return updateId; }
    public Long getQaItemId() { return qaItemId; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public String getSearchText() { return searchText; }
    public String getStatus() { return status; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
