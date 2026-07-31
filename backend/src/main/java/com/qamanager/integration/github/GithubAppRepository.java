package com.qamanager.integration.github;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GithubAppRepository extends JpaRepository<GithubApp, Long> {

    /** 단일 행 운용 — 가장 먼저 저장된 앱을 사용한다. */
    Optional<GithubApp> findTopByOrderByIdAsc();
}
