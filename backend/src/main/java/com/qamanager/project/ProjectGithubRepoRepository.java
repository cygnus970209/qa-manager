package com.qamanager.project;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ProjectGithubRepoRepository extends JpaRepository<ProjectGithubRepo, Long> {

    List<ProjectGithubRepo> findAllByProjectIdOrderByIdAsc(Long projectId);

    List<ProjectGithubRepo> findAllByProjectIdInOrderByIdAsc(Collection<Long> projectIds);

    /** 연결 목록 전체 교체용 즉시 삭제 (같은 flush 내 insert 와의 순서 문제 회피). */
    @Modifying
    @Query("delete from ProjectGithubRepo r where r.projectId = :projectId")
    void deleteAllByProjectId(@Param("projectId") Long projectId);
}
