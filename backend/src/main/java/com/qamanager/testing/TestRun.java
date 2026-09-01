package com.qamanager.testing;

import com.qamanager.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDateTime;

/** 업데이트(릴리즈)별 테스트 실행 1회. 실행 항목은 test_run_case 스냅샷. */
@Getter
@Entity
@Table(name = "test_run")
public class TestRun extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "update_id", nullable = false)
    private Long updateId;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    protected TestRun() {}

    public TestRun(Long updateId, String name) {
        this.updateId = updateId;
        this.name = name;
    }

    public void setClosed(boolean closed) {
        this.closedAt = closed ? LocalDateTime.now() : null;
    }
}
