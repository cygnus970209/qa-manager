package com.qamanager.testing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TestCaseRepository extends JpaRepository<TestCase, Long> {
    List<TestCase> findByProjectIdOrderByIdAsc(Long projectId);
    List<TestCase> findByFlowId(Long flowId);
    List<TestCase> findByIdIn(List<Long> ids);
}
