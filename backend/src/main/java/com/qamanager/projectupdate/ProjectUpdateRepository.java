package com.qamanager.projectupdate;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectUpdateRepository extends JpaRepository<ProjectUpdate, Long> {
    List<ProjectUpdate> findAllByProjectIdOrderByCreatedAtDesc(Long projectId);
}
