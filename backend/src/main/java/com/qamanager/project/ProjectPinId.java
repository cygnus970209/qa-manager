package com.qamanager.project;

import java.io.Serializable;
import java.util.Objects;

public class ProjectPinId implements Serializable {

    private Long project;
    private Long member;

    public ProjectPinId() {}

    public ProjectPinId(Long project, Long member) {
        this.project = project;
        this.member = member;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProjectPinId that)) return false;
        return Objects.equals(project, that.project) && Objects.equals(member, that.member);
    }

    @Override
    public int hashCode() {
        return Objects.hash(project, member);
    }
}
