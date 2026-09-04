package com.qamanager.project;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * 사용자별 프로젝트 순서 (사이드바 "프로젝트 순서 변경").
 * 순서를 저장한 프로젝트만 행이 있고, 없는 프로젝트는 기본 정렬 뒤에 붙는다 (ProjectService.list).
 */
@Entity
@Table(name = "project_member_order")
@IdClass(ProjectMemberOrderId.class)
public class ProjectMemberOrder {

    @Id
    @Column(name = "member_id")
    private Long memberId;

    @Id
    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected ProjectMemberOrder() {}

    public ProjectMemberOrder(Long memberId, Long projectId, int sortOrder) {
        this.memberId = memberId;
        this.projectId = projectId;
        this.sortOrder = sortOrder;
    }

    public Long getMemberId() { return memberId; }
    public Long getProjectId() { return projectId; }
    public int getSortOrder() { return sortOrder; }
}
