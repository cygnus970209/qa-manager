package com.qamanager.integration.github;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface QaGithubIssueRepository extends JpaRepository<QaGithubIssue, Long> {

    Optional<QaGithubIssue> findByQaItemId(Long qaItemId);

    boolean existsByQaItemId(Long qaItemId);
}
