package com.qamanager.testing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TestRunRepository extends JpaRepository<TestRun, Long> {
    List<TestRun> findByUpdateIdOrderByIdDesc(Long updateId);

    @Query("""
        select r from TestRun r
        where r.updateId in (select u.id from ProjectUpdate u where u.project.id = :projectId)
        order by r.id desc
        """)
    List<TestRun> findByProjectId(@Param("projectId") Long projectId);
}
