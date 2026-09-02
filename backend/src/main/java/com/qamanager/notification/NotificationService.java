package com.qamanager.notification;

import com.qamanager.common.ApiException;
import com.qamanager.member.TeamMember;
import com.qamanager.member.TeamMemberRepository;
import com.qamanager.notification.teams.TeamsNotifier;
import com.qamanager.project.Project;
import com.qamanager.project.ProjectRepository;
import com.qamanager.qa.comment.QaCommentService;
import com.qamanager.qa.item.QaItem;
import com.qamanager.qa.item.QaItemRepository;
import com.qamanager.qa.item.QaItemService;
import com.qamanager.qa.shared.QaStatus;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final TeamMemberRepository memberRepository;
    private final ProjectRepository projectRepository;
    private final QaItemRepository qaRepository;
    private final SseEmitterRegistry sse;
    private final TeamsNotifier teamsNotifier;

    public NotificationService(NotificationRepository notificationRepository,
                               TeamMemberRepository memberRepository,
                               ProjectRepository projectRepository,
                               QaItemRepository qaRepository,
                               SseEmitterRegistry sse,
                               TeamsNotifier teamsNotifier) {
        this.notificationRepository = notificationRepository;
        this.memberRepository = memberRepository;
        this.projectRepository = projectRepository;
        this.qaRepository = qaRepository;
        this.sse = sse;
        this.teamsNotifier = teamsNotifier;
    }

    @Transactional(readOnly = true)
    public List<NotificationDto.Response> listMine(Long memberId) {
        return notificationRepository.findAllByRecipientIdOrderByCreatedAtDesc(memberId).stream()
            .map(NotificationDto.Response::from).toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long memberId) {
        return notificationRepository.countByRecipientIdAndReadFalse(memberId);
    }

    @Transactional
    public NotificationDto.Response markRead(Long notificationId, Long memberId) {
        Notification n = notificationRepository.findById(notificationId)
            .orElseThrow(() -> ApiException.notFound("알림을 찾을 수 없습니다. id=" + notificationId));
        if (!n.getRecipient().getId().equals(memberId)) {
            throw ApiException.forbidden("본인의 알림만 조작할 수 있습니다.");
        }
        n.markRead();
        return NotificationDto.Response.from(n);
    }

    /** 본인의 안 읽은 알림을 모두 읽음 처리. */
    @Transactional
    public void markAllRead(Long memberId) {
        notificationRepository.findAllByRecipientIdAndReadFalse(memberId)
            .forEach(Notification::markRead);
    }

    /* ─────── 이벤트 → 알림 발행 ───────
     * title = QA 제목, message = 본문. (알림센터 첫 줄 / 데스크톱·Teams 알림 제목 = title)
     * 코멘트류 본문은 "<문구>: <댓글 발췌>" — 발췌는 공백 정리 후 EXCERPT_MAX 자로 자른다.
     */

    /** 댓글 발췌 최대 길이. message 컬럼(500)에 문구를 더해도 여유가 있게 잡는다. */
    static final int EXCERPT_MAX = 200;
    static final int MESSAGE_MAX = 500;
    static final int TITLE_MAX = 200;

    @EventListener
    @Transactional
    public void onQaCreated(QaItemService.QaCreatedEvent ev) {
        // 신규 QA 등록 → tester + assignee1 + assignee2 모두에게 (actor 제외)
        String msg = "새 QA가 등록되었습니다";
        Set<Long> recipients = distinctRecipients(ev.actorMemberId(),
            ev.testerMemberId(), ev.assignee1MemberId(), ev.assignee2MemberId());
        for (Long rid : recipients) {
            publishOne(rid, ev.actorMemberId(), "qa", ev.title(), msg, ev.projectId(), ev.qaItemId());
        }
    }

    @EventListener
    @Transactional
    public void onQaStatusChanged(QaItemService.QaStatusChangedEvent ev) {
        // 상태 변경 → tester + assignee1 + assignee2 모두에게 (actor 제외)
        // ev.newStatus() 는 영어 code 이므로 한글 라벨로 변환 (알림/Teams 메세지 문구용).
        String msg = "QA 상태 변경 → " + QaStatus.labelOf(ev.newStatus());
        Set<Long> recipients = distinctRecipients(ev.actorMemberId(),
            ev.testerMemberId(), ev.assignee1MemberId(), ev.assignee2MemberId());
        for (Long rid : recipients) {
            publishOne(rid, ev.actorMemberId(), "qa", ev.title(), msg, ev.projectId(), ev.qaItemId());
        }
    }

    /** 조건 1: 담당자가 새로 지정/변경될 때 — 해당 담당자에게만 */
    @EventListener
    @Transactional
    public void onQaAssigneeChanged(QaItemService.QaAssigneeChangedEvent ev) {
        if (ev.assigneeMemberId() == null || ev.assigneeMemberId().equals(ev.actorMemberId())) return;
        String msg = "QA가 배정되었습니다";
        publishOne(ev.assigneeMemberId(), ev.actorMemberId(), "qa", ev.title(), msg, ev.projectId(), ev.qaItemId());
    }

    /** 조건 2: QA에 코멘트가 달렸을 때 → tester + assignee1 + assignee2 모두에게 */
    @EventListener
    @Transactional
    public void onQaCommentCreated(QaCommentService.QaCommentCreatedEvent ev) {
        String msg = withExcerpt("새 코멘트가 달렸습니다", ev.content());
        Set<Long> recipients = distinctRecipients(ev.actorMemberId(),
            ev.testerMemberId(), ev.assignee1MemberId(), ev.assignee2MemberId());
        for (Long rid : recipients) {
            publishOne(rid, ev.actorMemberId(), "comment", ev.qaTitle(), msg, ev.projectId(), ev.qaItemId());
        }
    }

    /** 조건 3: 내 코멘트에 답글이 달렸을 때 */
    @EventListener
    @Transactional
    public void onQaCommentReplied(QaCommentService.QaCommentRepliedEvent ev) {
        if (ev.parentAuthorMemberId() == null || ev.parentAuthorMemberId().equals(ev.actorMemberId())) return;
        String msg = withExcerpt("내 코멘트에 답글이 달렸습니다", ev.content());
        publishOne(ev.parentAuthorMemberId(), ev.actorMemberId(), "reply", ev.qaTitle(), msg, ev.projectId(), ev.qaItemId());
    }

    /** 조건 4: 코멘트 본문에 @멘션 됐을 때. 발행 측에서 actor/기존 수신자 이미 제외. */
    @EventListener
    @Transactional
    public void onQaCommentMentioned(QaCommentService.QaCommentMentionedEvent ev) {
        if (ev.mentionedMemberId() == null) return;
        String msg = withExcerpt("코멘트에서 언급되었습니다", ev.content());
        publishOne(ev.mentionedMemberId(), ev.actorMemberId(), "mention", ev.qaTitle(), msg, ev.projectId(), ev.qaItemId());
    }

    /** "<문구>: <댓글 발췌>". 발췌가 비면 문구만. package-private for test. */
    static String withExcerpt(String phrase, String content) {
        String ex = excerpt(content, EXCERPT_MAX);
        return ex.isEmpty() ? phrase : phrase + ": " + ex;
    }

    /**
     * 댓글 본문 발췌: 줄바꿈·연속 공백을 한 칸으로 정리하고 max 자를 넘으면 잘라 "…" 을 붙인다.
     * (@멘션은 본문에 평문 "@이름" 으로 들어 있으므로 그대로 둔다.) package-private for test.
     */
    static String excerpt(String content, int max) {
        if (content == null) return "";
        String s = content.replaceAll("\\s+", " ").trim();
        if (s.length() <= max) return s;
        int cut = Math.max(0, max - 1);
        if (cut > 0 && Character.isHighSurrogate(s.charAt(cut - 1))) cut--; // 이모지 등 서로게이트 쌍 보호
        return s.substring(0, cut).stripTrailing() + "…";
    }

    private static String truncate(String s, int max) {
        if (s == null || s.length() <= max) return s;
        return s.substring(0, max - 1) + "…";
    }

    /** actor 본인 제외 + null 제외 + 중복 제거. 삽입 순서 유지 (LinkedHashSet). package-private for test. */
    Set<Long> distinctRecipients(Long actorId, Long... candidates) {
        Set<Long> set = new LinkedHashSet<>();
        for (Long id : candidates) {
            if (id == null) continue;
            if (id.equals(actorId)) continue;
            set.add(id);
        }
        return set;
    }

    private void publishOne(Long recipientId, Long actorId, String type, String title, String message,
                            Long projectId, Long qaItemId) {
        TeamMember recipient = memberRepository.findById(recipientId).orElse(null);
        if (recipient == null) return;
        TeamMember actor = actorId == null ? null : memberRepository.findById(actorId).orElse(null);
        Project project = projectId == null ? null : projectRepository.findById(projectId).orElse(null);
        QaItem qa = qaItemId == null ? null : qaRepository.findById(qaItemId).orElse(null);

        Notification n = notificationRepository.save(
            new Notification(recipient, actor, type, truncate(title, TITLE_MAX), truncate(message, MESSAGE_MAX), project, qa)
        );
        sse.publish(recipientId, "notification", NotificationDto.Response.from(n));

        // Teams 발송은 트랜잭션 커밋 후에 (rollback 시 발송 방지). 비동기 + 실패 격리.
        TeamsNotifier.NotificationPayload payload = TeamsNotifier.NotificationPayload.from(n);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() {
                    teamsNotifier.send(recipientId, payload);
                }
            });
        } else {
            teamsNotifier.send(recipientId, payload);
        }
    }
}
