package com.qamanager.qa.comment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QaCommentRepository extends JpaRepository<QaComment, Long> {
    List<QaComment> findAllByQaItemIdOrderByCreatedAtAsc(Long qaItemId);
}
