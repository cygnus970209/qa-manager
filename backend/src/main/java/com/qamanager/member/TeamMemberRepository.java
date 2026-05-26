package com.qamanager.member;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {

    /** 활성 멤버 username 조회 (로그인 등). 소프트 삭제된 멤버는 제외. */
    Optional<TeamMember> findByUsernameAndDeletedAtIsNull(String username);

    /** 활성 멤버 단건 조회. 인증/멤버 목록/관리에서 사용. */
    Optional<TeamMember> findByIdAndDeletedAtIsNull(Long id);

    /** 활성 멤버 전체 목록. */
    List<TeamMember> findAllByDeletedAtIsNull();

    /** username 중복 검사용. unique 인덱스가 deleted 까지 포함하므로 모든 row 대상. */
    boolean existsByUsername(String username);

    /** email 로 활성 멤버 조회 (Teams 알림 매핑 검증용). */
    Optional<TeamMember> findByEmailAndDeletedAtIsNull(String email);

    /** AAD Object ID 로 활성 멤버 조회 (Bot 설치 이벤트에서 conversation 캐싱용). */
    Optional<TeamMember> findByTeamsUserIdAndDeletedAtIsNull(String teamsUserId);
}
