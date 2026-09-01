package com.qamanager.testing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TestSuiteRepository extends JpaRepository<TestSuite, Long> {
    List<TestSuite> findByProjectIdOrderBySortOrderAscIdAsc(Long projectId);
}
