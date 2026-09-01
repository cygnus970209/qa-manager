package com.qamanager.testing;

import com.qamanager.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * 프로젝트급 재사용 테스트 케이스.
 * - steps_json: [{action, expected}] 배열 (프론트가 스키마 소유 — 백엔드는 통짜 JSON 보관)
 * - origin=FLOW: test_flow 의 경로에서 생성됨. 원본 그래프가 바뀌면 flow_stale=true 로 표시만 하고
 *   자동 재생성은 하지 않는다(사용자 수정 보호).
 */
@Getter
@Entity
@Table(name = "test_case")
public class TestCase extends BaseEntity {

    public enum Origin { MANUAL, FLOW }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "suite_id")
    private Long suiteId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "precondition", columnDefinition = "TEXT")
    private String precondition;

    @Column(name = "steps_json", nullable = false, columnDefinition = "LONGTEXT")
    private String stepsJson;

    /** QaPriority code (low/medium/high/critical) — QA 항목과 동일 어휘. */
    @Column(name = "priority", nullable = false, length = 20)
    private String priority;

    @Column(name = "origin", nullable = false, length = 20)
    private String origin = Origin.MANUAL.name();

    @Column(name = "flow_id")
    private Long flowId;

    @Column(name = "flow_stale", nullable = false)
    private boolean flowStale;

    protected TestCase() {}

    public TestCase(Long projectId, Long suiteId, String title, String precondition,
                    String stepsJson, String priority, Origin origin, Long flowId) {
        this.projectId = projectId;
        this.suiteId = suiteId;
        this.title = title;
        this.precondition = precondition;
        this.stepsJson = stepsJson;
        this.priority = priority;
        this.origin = origin.name();
        this.flowId = flowId;
    }

    public void update(Long suiteId, String title, String precondition, String stepsJson, String priority) {
        if (suiteId != null) this.suiteId = suiteId == 0 ? null : suiteId;
        if (title != null) this.title = title;
        if (precondition != null) this.precondition = precondition.isBlank() ? null : precondition;
        if (stepsJson != null) this.stepsJson = stepsJson;
        if (priority != null) this.priority = priority;
    }

    public void markFlowStale(boolean stale) {
        this.flowStale = stale;
    }
}
