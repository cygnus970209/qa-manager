package com.qamanager.member;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {
    Optional<TeamMember> findByUsername(String username);
    boolean existsByUsername(String username);
}
