package com.qamanager.qa.item;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface QaItemRepository extends JpaRepository<QaItem, Long>, JpaSpecificationExecutor<QaItem> {

    /**
     * 목록 조회는 DTO 변환에 쓰이는 업데이트·테스터·담당자를 한 쿼리로 조인해 가져온다.
     * (없으면 QA 건수만큼 지연 로딩 쿼리가 나간다 — 700건 프로젝트에서 상세 진입이 수 초 걸리던 원인.)
     * 이미지 컬렉션은 QaItem.images 의 @BatchSize 로 묶어 읽는다.
     */
    @Override
    @EntityGraph(attributePaths = {"projectUpdate", "tester", "assignee1", "assignee2"})
    List<QaItem> findAll(Specification<QaItem> spec);

    @Override
    @EntityGraph(attributePaths = {"projectUpdate", "tester", "assignee1", "assignee2"})
    Page<QaItem> findAll(Specification<QaItem> spec, Pageable pageable);

    /**
     * 대시보드 집계용 (프로젝트 × 상태 × 우선순위)별 건수.
     * memberId 가 null 이면 전체, 지정하면 해당 멤버가 테스터/담당자인 QA 만 집계한다.
     */
    @Query("""
        select u.project.id, q.status, q.priority, count(q)
        from QaItem q join q.projectUpdate u
        where :memberId is null
           or q.tester.id = :memberId
           or q.assignee1.id = :memberId
           or q.assignee2.id = :memberId
        group by u.project.id, q.status, q.priority
        """)
    List<Object[]> dashboardRows(@Param("memberId") Long memberId);
}
