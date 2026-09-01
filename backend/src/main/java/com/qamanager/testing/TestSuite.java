package com.qamanager.testing;

import com.qamanager.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

/** 프로젝트별 테스트 케이스 묶음(폴더 1단계). */
@Getter
@Entity
@Table(name = "test_suite")
public class TestSuite extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected TestSuite() {}

    public TestSuite(Long projectId, String name, int sortOrder) {
        this.projectId = projectId;
        this.name = name;
        this.sortOrder = sortOrder;
    }

    public void update(String name, Integer sortOrder) {
        if (name != null) this.name = name;
        if (sortOrder != null) this.sortOrder = sortOrder;
    }
}
