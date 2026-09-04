package com.qamanager.project;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectMemberOrderRepository extends JpaRepository<ProjectMemberOrder, ProjectMemberOrderId> {

    List<ProjectMemberOrder> findAllByMemberIdOrderBySortOrderAsc(Long memberId);

    void deleteByMemberId(Long memberId);
}
