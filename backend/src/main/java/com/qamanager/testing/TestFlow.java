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
 * 기획 워크플로우 그래프. 노드/엣지는 프론트(Vue Flow)가 스키마를 소유하는 JSON 통짜 저장.
 * (릴레이션으로 쪼개지 않는 이유: 조인/검색이 없고 경로 열거는 클라이언트에서 수행)
 */
@Getter
@Entity
@Table(name = "test_flow")
public class TestFlow extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    /** 연결된 업데이트(선택). 케이스 생성 시 기본 스위트 이름 결정에 사용. */
    @Column(name = "update_id")
    private Long updateId;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "graph_json", nullable = false, columnDefinition = "LONGTEXT")
    private String graphJson;

    protected TestFlow() {}

    public TestFlow(Long projectId, Long updateId, String name, String graphJson) {
        this.projectId = projectId;
        this.updateId = updateId;
        this.name = name;
        this.graphJson = graphJson;
    }

    /** graph 저장 여부를 반환 — true 면 이 flow 에서 생성된 케이스에 stale 표시 필요. */
    public boolean update(String name, Long updateId, String graphJson) {
        if (name != null) this.name = name;
        if (updateId != null) this.updateId = updateId == 0 ? null : updateId;
        if (graphJson != null) {
            boolean changed = !graphJson.equals(this.graphJson);
            this.graphJson = graphJson;
            return changed;
        }
        return false;
    }
}
