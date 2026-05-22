package com.qamanager.qa.item;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QaHistoryRepository extends JpaRepository<QaHistory, Long> {
    List<QaHistory> findAllByQaItemIdOrderByChangedAtDesc(Long qaItemId);
}
