package com.qamanager.project;

import java.io.Serializable;
import java.util.Objects;

public class ProjectMemberOrderId implements Serializable {

    private Long memberId;
    private Long projectId;

    public ProjectMemberOrderId() {}

    public ProjectMemberOrderId(Long memberId, Long projectId) {
        this.memberId = memberId;
        this.projectId = projectId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProjectMemberOrderId that)) return false;
        return Objects.equals(memberId, that.memberId) && Objects.equals(projectId, that.projectId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(memberId, projectId);
    }
}
