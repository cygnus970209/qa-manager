package com.qamanager.qa.comment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QaCommentReactionRepository extends JpaRepository<QaCommentReaction, QaCommentReactionId> {
    List<QaCommentReaction> findAllByCommentIdIn(List<Long> commentIds);
    Optional<QaCommentReaction> findByCommentIdAndEmojiAndMemberId(Long commentId, String emoji, Long memberId);
    void deleteByCommentIdAndEmojiAndMemberId(Long commentId, String emoji, Long memberId);
}
