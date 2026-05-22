package com.qamanager.project;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ProjectPinRepository extends JpaRepository<ProjectPin, ProjectPinId> {

    @Query("select pp.project.id from ProjectPin pp where pp.member.id = :memberId")
    List<Long> findPinnedProjectIdsByMember(Long memberId);

    Optional<ProjectPin> findByProjectIdAndMemberId(Long projectId, Long memberId);

    void deleteByProjectIdAndMemberId(Long projectId, Long memberId);
}
