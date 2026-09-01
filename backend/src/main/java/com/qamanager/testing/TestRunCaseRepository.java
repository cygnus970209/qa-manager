package com.qamanager.testing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface TestRunCaseRepository extends JpaRepository<TestRunCase, Long> {
    List<TestRunCase> findByRunIdOrderBySortOrderAscIdAsc(Long runId);

    @Query("select c.runId, c.result, count(c) from TestRunCase c where c.runId in :runIds group by c.runId, c.result")
    List<Object[]> countResultsByRunIds(@Param("runIds") Collection<Long> runIds);
}
