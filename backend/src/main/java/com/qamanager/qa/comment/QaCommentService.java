package com.qamanager.qa.comment;

import com.qamanager.common.ApiException;
import com.qamanager.member.TeamMember;
import com.qamanager.member.TeamMemberRepository;
import com.qamanager.qa.item.QaItem;
import com.qamanager.qa.item.QaItemRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class QaCommentService {

    private final QaCommentRepository commentRepository;
    private final QaCommentReactionRepository reactionRepository;
    private final QaItemRepository qaRepository;
    private final TeamMemberRepository memberRepository;
    private final ApplicationEventPublisher events;

    public QaCommentService(QaCommentRepository commentRepository,
                            QaCommentReactionRepository reactionRepository,
                            QaItemRepository qaRepository,
                            TeamMemberRepository memberRepository,
                            ApplicationEventPublisher events) {
        this.commentRepository = commentRepository;
        this.reactionRepository = reactionRepository;
        this.qaRepository = qaRepository;
        this.memberRepository = memberRepository;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<CommentDto.Response> list(Long qaItemId) {
        if (!qaRepository.existsById(qaItemId)) {
            throw ApiException.notFound("QA 항목을 찾을 수 없습니다. id=" + qaItemId);
        }
        List<QaComment> comments = commentRepository.findAllByQaItemIdOrderByCreatedAtAsc(qaItemId);
        List<Long> ids = comments.stream().map(QaComment::getId).toList();
        Map<Long, Map<String, List<Long>>> reactionsByComment = new HashMap<>();
        if (!ids.isEmpty()) {
            for (QaCommentReaction r : reactionRepository.findAllByCommentIdIn(ids)) {
                reactionsByComment
                    .computeIfAbsent(r.getComment().getId(), k -> new LinkedHashMap<>())
                    .computeIfAbsent(r.getEmoji(), k -> new java.util.ArrayList<>())
                    .add(r.getMember().getId());
            }
        }
        return comments.stream().map(c -> toResponse(c, reactionsByComment.get(c.getId()))).toList();
    }

    @Transactional
    public CommentDto.Response create(Long qaItemId, CommentDto.CreateRequest req, Long currentMemberId) {
        QaItem item = qaRepository.findById(qaItemId)
            .orElseThrow(() -> ApiException.notFound("QA 항목을 찾을 수 없습니다. id=" + qaItemId));
        QaComment parent = null;
        if (req.parentId() != null) {
            parent = commentRepository.findById(req.parentId())
                .orElseThrow(() -> ApiException.notFound("부모 댓글을 찾을 수 없습니다. id=" + req.parentId()));
            if (!parent.getQaItem().getId().equals(qaItemId)) {
                throw ApiException.badRequest("부모 댓글이 같은 QA 항목에 속하지 않습니다.");
            }
        }
        TeamMember author = memberRepository.findByIdAndDeletedAtIsNull(currentMemberId)
            .orElseThrow(() -> ApiException.unauthorized("로그인 멤버가 존재하지 않습니다."));
        QaComment c = new QaComment(item, parent, author, req.content());
        c.replaceImages(req.images());
        QaComment saved = commentRepository.save(c);

        Long projectId = item.getProjectUpdate().getProject().getId();
        if (parent == null) {
            // 새 루트 코멘트 → QA 담당자에게 알림 (조건 2)
            Long assigneeId = item.getAssignee() == null ? null : item.getAssignee().getId();
            events.publishEvent(new QaCommentCreatedEvent(
                saved.getId(), item.getId(), projectId, item.getTitle(),
                currentMemberId, assigneeId
            ));
        } else {
            // 답글 → 부모 코멘트 작성자에게 알림 (조건 3)
            Long parentAuthorId = parent.getAuthor().getId();
            events.publishEvent(new QaCommentRepliedEvent(
                saved.getId(), parent.getId(), item.getId(), projectId, item.getTitle(),
                currentMemberId, parentAuthorId
            ));
        }
        return toResponse(saved, null);
    }

    @Transactional
    public CommentDto.Response update(Long commentId, CommentDto.UpdateRequest req, Long currentMemberId) {
        QaComment c = findCommentOrThrow(commentId);
        if (!c.getAuthor().getId().equals(currentMemberId)) {
            throw ApiException.forbidden("본인 댓글만 수정할 수 있습니다.");
        }
        c.setContent(req.content());
        if (req.images() != null) c.replaceImages(req.images());
        Map<String, List<Long>> reactions = collectReactions(commentId);
        return toResponse(c, reactions);
    }

    @Transactional
    public void delete(Long commentId, Long currentMemberId) {
        QaComment c = findCommentOrThrow(commentId);
        if (!c.getAuthor().getId().equals(currentMemberId)) {
            throw ApiException.forbidden("본인 댓글만 삭제할 수 있습니다.");
        }
        commentRepository.delete(c);
    }

    @Transactional
    public CommentDto.Response toggleReaction(Long commentId, String emoji, Long currentMemberId) {
        QaComment c = findCommentOrThrow(commentId);
        reactionRepository.findByCommentIdAndEmojiAndMemberId(commentId, emoji, currentMemberId)
            .ifPresentOrElse(
                reactionRepository::delete,
                () -> {
                    TeamMember m = memberRepository.findByIdAndDeletedAtIsNull(currentMemberId)
                        .orElseThrow(() -> ApiException.unauthorized("로그인 멤버가 존재하지 않습니다."));
                    reactionRepository.save(new QaCommentReaction(c, emoji, m));
                }
            );
        return toResponse(c, collectReactions(commentId));
    }

    private QaComment findCommentOrThrow(Long id) {
        return commentRepository.findById(id)
            .orElseThrow(() -> ApiException.notFound("댓글을 찾을 수 없습니다. id=" + id));
    }

    private Map<String, List<Long>> collectReactions(Long commentId) {
        Map<String, List<Long>> map = new LinkedHashMap<>();
        for (QaCommentReaction r : reactionRepository.findAllByCommentIdIn(List.of(commentId))) {
            map.computeIfAbsent(r.getEmoji(), k -> new java.util.ArrayList<>()).add(r.getMember().getId());
        }
        return map;
    }

    /* ─────── 이벤트 (Notification 도메인에서 구독) ─────── */
    public record QaCommentCreatedEvent(Long commentId, Long qaItemId, Long projectId,
                                        String qaTitle,
                                        Long actorMemberId, Long assigneeMemberId) {}

    public record QaCommentRepliedEvent(Long commentId, Long parentCommentId, Long qaItemId,
                                        Long projectId, String qaTitle,
                                        Long actorMemberId, Long parentAuthorMemberId) {}

    private CommentDto.Response toResponse(QaComment c, Map<String, List<Long>> reactions) {
        CommentDto.AuthorSummary author = new CommentDto.AuthorSummary(
            c.getAuthor().getId(), c.getAuthor().getName(), c.getAuthor().getAvatarUrl()
        );
        return new CommentDto.Response(
            c.getId(),
            c.getQaItem().getId(),
            c.getParent() == null ? null : c.getParent().getId(),
            author,
            c.getContent(),
            c.getImages().stream().map(QaCommentImage::getImageUrl).toList(),
            reactions == null ? Map.of() : reactions,
            c.getCreatedAt() != null ? c.getCreatedAt().toString() : null,
            c.getUpdatedAt() != null ? c.getUpdatedAt().toString() : null
        );
    }
}
