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

/**
 * 런 × 케이스 실행 항목. 생성 시점의 케이스 스냅샷(title/steps/priority)을 보관해
 * 원본 케이스가 이후 수정/삭제돼도 실행 기록이 보존된다.
 */
@Getter
@Entity
@Table(name = "test_run_case")
public class TestRunCase extends BaseEntity {

    public enum Result { PENDING, PASS, FAIL, BLOCKED, SKIP }

    public enum Platform { PC, ANDROID, IOS }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Column(name = "case_id")
    private Long caseId;

    /** 실행 플랫폼 (PC/ANDROID/IOS). null 이면 플랫폼 구분 없는 공통 실행. */
    @Column(name = "platform", length = 20)
    private String platform;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "precondition", columnDefinition = "TEXT")
    private String precondition;

    @Column(name = "steps_json", nullable = false, columnDefinition = "LONGTEXT")
    private String stepsJson;

    @Column(name = "priority", nullable = false, length = 20)
    private String priority;

    @Column(name = "result", nullable = false, length = 20)
    private String result = Result.PENDING.name();

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    /** FAIL 에서 생성한 QA 항목 링크. */
    @Column(name = "qa_item_id")
    private Long qaItemId;

    @Column(name = "executed_at")
    private LocalDateTime executedAt;

    protected TestRunCase() {}

    public TestRunCase(Long runId, TestCase source, int sortOrder, String platform) {
        this.runId = runId;
        this.caseId = source.getId();
        this.sortOrder = sortOrder;
        this.platform = platform;
        this.title = source.getTitle();
        this.precondition = source.getPrecondition();
        this.stepsJson = source.getStepsJson();
        this.priority = source.getPriority();
    }

    public void recordResult(String result, String note, Long qaItemId) {
        if (result != null) {
            this.result = result;
            this.executedAt = Result.PENDING.name().equals(result) ? null : LocalDateTime.now();
        }
        if (note != null) this.note = note.isBlank() ? null : note;
        if (qaItemId != null) this.qaItemId = qaItemId == 0 ? null : qaItemId;
    }
}
