package com.qamanager.testing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TestFlowRepository extends JpaRepository<TestFlow, Long> {
    List<TestFlow> findByProjectIdOrderByIdDesc(Long projectId);
}
