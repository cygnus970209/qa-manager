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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        Set<Long> excludeForMention = new HashSet<>();
        excludeForMention.add(currentMemberId);
        if (parent == null) {
            // 새 루트 코멘트 → tester + assignee1 + assignee2 모두에게 알림 (조건 2)
            events.publishEvent(new QaCommentCreatedEvent(
                saved.getId(), item.getId(), projectId, item.getTitle(), saved.getContent(),
                currentMemberId,
                item.getTester()    == null ? null : item.getTester().getId(),
                item.getAssignee1() == null ? null : item.getAssignee1().getId(),
                item.getAssignee2() == null ? null : item.getAssignee2().getId()
            ));
            if (item.getTester()    != null) excludeForMention.add(item.getTester().getId());
            if (item.getAssignee1() != null) excludeForMention.add(item.getAssignee1().getId());
            if (item.getAssignee2() != null) excludeForMention.add(item.getAssignee2().getId());
        } else {
            // 답글 → 부모 코멘트 작성자에게 알림 (조건 3)
            Long parentAuthorId = parent.getAuthor().getId();
            events.publishEvent(new QaCommentRepliedEvent(
                saved.getId(), parent.getId(), item.getId(), projectId, item.getTitle(), saved.getContent(),
                currentMemberId, parentAuthorId
            ));
            excludeForMention.add(parentAuthorId);
        }

        // @멘션 알림 — 프론트가 보내준 mentionedMemberIds 사용 (ID 기반). 자동완성 pick 한 멤버만 알림.
        // 기존 comment/reply 알림 수신자와 작성자 본인은 제외해 중복 방지.
        if (req.mentionedMemberIds() != null) {
            Set<Long> sent = new LinkedHashSet<>();
            for (Long mentionedId : req.mentionedMemberIds()) {
                if (mentionedId == null) continue;
                if (excludeForMention.contains(mentionedId)) continue;
                if (!sent.add(mentionedId)) continue; // 중복 제거
                // 존재하는 활성 멤버인지 검증 (잘못된 id 무시)
                if (memberRepository.findByIdAndDeletedAtIsNull(mentionedId).isEmpty()) continue;
                events.publishEvent(new QaCommentMentionedEvent(
                    saved.getId(), item.getId(), projectId, item.getTitle(), saved.getContent(),
                    currentMemberId, mentionedId
                ));
            }
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
                                        String qaTitle, String content,
                                        Long actorMemberId,
                                        Long testerMemberId,
                                        Long assignee1MemberId,
                                        Long assignee2MemberId) {}

    public record QaCommentRepliedEvent(Long commentId, Long parentCommentId, Long qaItemId,
                                        Long projectId, String qaTitle, String content,
                                        Long actorMemberId, Long parentAuthorMemberId) {}

    /** 코멘트 본문에 @멘션 된 멤버에게 발행. 멤버 1명당 1건. */
    public record QaCommentMentionedEvent(Long commentId, Long qaItemId, Long projectId,
                                          String qaTitle, String content,
                                          Long actorMemberId, Long mentionedMemberId) {}

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
