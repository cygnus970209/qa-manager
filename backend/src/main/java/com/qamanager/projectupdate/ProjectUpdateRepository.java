package com.qamanager.projectupdate;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectUpdateRepository extends JpaRepository<ProjectUpdate, Long> {
    List<ProjectUpdate> findAllByProjectIdOrderBySortOrderAscCreatedAtDesc(Long projectId);

    Optional<ProjectUpdate> findFirstByProjectIdOrderBySortOrderAsc(Long projectId);
}
