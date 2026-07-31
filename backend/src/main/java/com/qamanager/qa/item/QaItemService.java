package com.qamanager.qa.item;

import com.qamanager.common.ApiException;
import com.qamanager.integration.github.QaGithubIssueRepository;
import com.qamanager.member.TeamMember;
import com.qamanager.member.TeamMemberRepository;
import com.qamanager.projectupdate.ProjectUpdate;
import com.qamanager.projectupdate.ProjectUpdateRepository;
import com.qamanager.qa.shared.QaPriority;
import com.qamanager.qa.shared.QaStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class QaItemService {

    private final QaItemRepository qaRepository;
    private final QaHistoryRepository historyRepository;
    private final ProjectUpdateRepository updateRepository;
    private final TeamMemberRepository memberRepository;
    private final QaGithubIssueRepository githubIssueRepository;
    private final ApplicationEventPublisher events;

    public QaItemService(QaItemRepository qaRepository,
                         QaHistoryRepository historyRepository,
                         ProjectUpdateRepository updateRepository,
                         TeamMemberRepository memberRepository,
                         QaGithubIssueRepository githubIssueRepository,
                         ApplicationEventPublisher events) {
        this.qaRepository = qaRepository;
        this.historyRepository = historyRepository;
        this.updateRepository = updateRepository;
        this.memberRepository = memberRepository;
        this.githubIssueRepository = githubIssueRepository;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<QaDto.Response> list(Long updateId, String status, String priority,
                                     Long assigneeId, Long testerId) {
        Specification<QaItem> spec = Specification.unrestricted();
        if (updateId != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("projectUpdate").get("id"), updateId));
        }
        if (status != null) {
            String code = QaStatus.valueOf(status.toUpperCase()).getCode();
            spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), code));
        }
        if (priority != null) {
            String code = QaPriority.valueOf(priority.toUpperCase()).getCode();
            spec = spec.and((root, q, cb) -> cb.equal(root.get("priority"), code));
        }
        if (assigneeId != null) {
            // assignee1 또는 assignee2 슬롯 어디에 있어도 매칭
            spec = spec.and((root, q, cb) -> cb.or(
                cb.equal(root.get("assignee1").get("id"), assigneeId),
                cb.equal(root.get("assignee2").get("id"), assigneeId)
            ));
        }
        if (testerId != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("tester").get("id"), testerId));
        }
        spec = spec.and((root, q, cb) -> {
            q.orderBy(cb.desc(root.get("createdAt")));
            return cb.conjunction();
        });
        return qaRepository.findAll(spec).stream().map(QaDto.Response::from).toList();
    }

    @Transactional(readOnly = true)
    public QaDto.Response get(Long id) {
        return QaDto.Response.from(findOrThrow(id), githubIssueOf(id));
    }

    @Transactional
    public QaDto.Response create(QaDto.CreateRequest req, Long currentMemberId) {
        ProjectUpdate update = updateRepository.findById(req.updateId())
            .orElseThrow(() -> ApiException.notFound("업데이트를 찾을 수 없습니다. id=" + req.updateId()));

        // tester: 명시 안 하면 현재 로그인 사용자 자동
        Long testerId = req.testerId() != null ? req.testerId() : currentMemberId;
        TeamMember tester = resolveMemberOrThrow(testerId);
        TeamMember assignee1 = resolveMemberOptional(req.assignee1Id());
        TeamMember assignee2 = resolveMemberOptional(req.assignee2Id());

        QaItem item = new QaItem(update, req.title(), req.description(), req.category(),
            req.status(), tester, assignee1, assignee2, req.priority());
        item.replaceImages(req.images());
        QaItem saved = qaRepository.save(item);

        events.publishEvent(new QaCreatedEvent(saved.getId(), update.getProject().getId(),
            saved.getTitle(), currentMemberId,
            tester == null ? null : tester.getId(),
            assignee1 == null ? null : assignee1.getId(),
            assignee2 == null ? null : assignee2.getId(),
            Boolean.TRUE.equals(req.createGithubIssue())));
        return QaDto.Response.from(saved);
    }

    @Transactional
    public QaDto.Response update(Long id, QaDto.UpdateRequest req, Long currentMemberId) {
        QaItem q = findOrThrow(id);
        TeamMember changedBy = memberRepository.findByIdAndDeletedAtIsNull(currentMemberId).orElse(null);
        List<QaHistory> diffs = new ArrayList<>();

        // 다른 업데이트(버전)로 이동. 이력엔 버전 문자열을 남긴다.
        if (req.updateId() != null && !req.updateId().equals(q.getProjectUpdate().getId())) {
            ProjectUpdate target = updateRepository.findById(req.updateId())
                .orElseThrow(() -> ApiException.notFound("업데이트를 찾을 수 없습니다. id=" + req.updateId()));
            diffs.add(new QaHistory(q, "update", q.getProjectUpdate().getVersion(), target.getVersion(), changedBy));
            q.setProjectUpdate(target);
        }

        if (req.title() != null && !req.title().equals(q.getTitle())) {
            diffs.add(new QaHistory(q, "title", q.getTitle(), req.title(), changedBy));
            q.setTitle(req.title());
        }
        if (req.description() != null && !Objects.equals(req.description(), q.getDescription())) {
            diffs.add(new QaHistory(q, "description", truncate(q.getDescription()), truncate(req.description()), changedBy));
            q.setDescription(req.description());
        }
        if (req.category() != null && !Objects.equals(req.category(), q.getCategory())) {
            diffs.add(new QaHistory(q, "category", q.getCategory(), req.category(), changedBy));
            q.setCategory(req.category());
        }
        if (req.status() != null && req.status() != q.getStatus()) {
            diffs.add(new QaHistory(q, "status", q.getStatus().getCode(), req.status().getCode(), changedBy));
            q.setStatus(req.status());
            events.publishEvent(new QaStatusChangedEvent(q.getId(), q.getProjectUpdate().getProject().getId(),
                q.getTitle(), q.getStatus().getCode(), currentMemberId,
                q.getTester() == null ? null : q.getTester().getId(),
                q.getAssignee1() == null ? null : q.getAssignee1().getId(),
                q.getAssignee2() == null ? null : q.getAssignee2().getId()));
        }
        if (req.priority() != null && req.priority() != q.getPriority()) {
            diffs.add(new QaHistory(q, "priority", q.getPriority().getCode(), req.priority().getCode(), changedBy));
            q.setPriority(req.priority());
        }

        // tester / assignee1 / assignee2 — 각 슬롯 독립 처리.
        // tester 도 배정 시 알림을 보낸다 (assignee 와 동일). 이전엔 notify=false 라 테스터만 알림 누락됐음.
        handleMemberSlot(q, "tester", q.getTester(), req.testerId(), req.clearTester(),
            diffs, changedBy, q::setTester, currentMemberId, /*notify=*/true);
        handleMemberSlot(q, "assignee1", q.getAssignee1(), req.assignee1Id(), req.clearAssignee1(),
            diffs, changedBy, q::setAssignee1, currentMemberId, /*notify=*/true);
        handleMemberSlot(q, "assignee2", q.getAssignee2(), req.assignee2Id(), req.clearAssignee2(),
            diffs, changedBy, q::setAssignee2, currentMemberId, /*notify=*/true);

        if (req.images() != null) {
            List<String> oldUrls = q.getImages().stream().map(QaItemImage::getImageUrl).toList();
            List<String> newUrls = req.images();
            Set<String> oldSet = new HashSet<>(oldUrls);
            Set<String> newSet = new HashSet<>(newUrls);
            for (String url : oldUrls) {
                if (!newSet.contains(url)) {
                    diffs.add(new QaHistory(q, "image_removed", truncate(url), null, changedBy));
                }
            }
            for (String url : newUrls) {
                if (!oldSet.contains(url)) {
                    diffs.add(new QaHistory(q, "image_added", null, truncate(url), changedBy));
                }
            }
            q.replaceImages(req.images());
        }
        if (!diffs.isEmpty()) historyRepository.saveAll(diffs);
        return QaDto.Response.from(q, githubIssueOf(id));
    }

    private interface MemberSetter { void set(TeamMember m); }

    private void handleMemberSlot(QaItem q, String fieldName, TeamMember current,
                                  Long newId, Boolean clear,
                                  List<QaHistory> diffs, TeamMember changedBy,
                                  MemberSetter setter, Long actorMemberId, boolean notify) {
        if (Boolean.TRUE.equals(clear)) {
            if (current != null) {
                diffs.add(new QaHistory(q, fieldName, current.getName(), null, changedBy));
                setter.set(null);
            }
            return;
        }
        if (newId == null) return; // 변경 없음
        Long currentId = current == null ? null : current.getId();
        if (Objects.equals(currentId, newId)) return;

        TeamMember next = resolveMemberOrThrow(newId);
        diffs.add(new QaHistory(q, fieldName,
            current == null ? null : current.getName(),
            next.getName(), changedBy));
        setter.set(next);
        if (notify) {
            events.publishEvent(new QaAssigneeChangedEvent(
                q.getId(), q.getProjectUpdate().getProject().getId(),
                q.getTitle(), actorMemberId, next.getId()
            ));
        }
    }

    @Transactional
    public void delete(Long id) {
        if (!qaRepository.existsById(id)) {
            throw ApiException.notFound("QA 항목을 찾을 수 없습니다. id=" + id);
        }
        qaRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<QaDto.HistoryResponse> history(Long qaItemId) {
        if (!qaRepository.existsById(qaItemId)) {
            throw ApiException.notFound("QA 항목을 찾을 수 없습니다. id=" + qaItemId);
        }
        return historyRepository.findAllByQaItemIdOrderByChangedAtDesc(qaItemId).stream()
            .map(QaDto.HistoryResponse::from).toList();
    }

    private QaItem findOrThrow(Long id) {
        return qaRepository.findById(id)
            .orElseThrow(() -> ApiException.notFound("QA 항목을 찾을 수 없습니다. id=" + id));
    }

    private QaDto.GithubIssue githubIssueOf(Long qaItemId) {
        return githubIssueRepository.findByQaItemId(qaItemId)
            .map(QaDto.GithubIssue::from)
            .orElse(null);
    }

    private TeamMember resolveMemberOptional(Long id) {
        if (id == null) return null;
        return resolveMemberOrThrow(id);
    }

    private TeamMember resolveMemberOrThrow(Long id) {
        return memberRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> ApiException.notFound("멤버를 찾을 수 없습니다. id=" + id));
    }

    private String truncate(String s) {
        if (s == null) return null;
        return s.length() <= 500 ? s : s.substring(0, 500);
    }

    /* ─────── 이벤트 (Notification / GitHub 연동 도메인에서 구독) ─────── */
    public record QaCreatedEvent(Long qaItemId, Long projectId, String title,
                                 Long actorMemberId,
                                 Long testerMemberId,
                                 Long assignee1MemberId,
                                 Long assignee2MemberId,
                                 boolean createGithubIssue) {}

    public record QaStatusChangedEvent(Long qaItemId, Long projectId, String title,
                                       String newStatus, Long actorMemberId,
                                       Long testerMemberId,
                                       Long assignee1MemberId,
                                       Long assignee2MemberId) {}

    /** 담당자(assignee1/2) 가 새로 지정/변경됐을 때만 발행. tester 변경은 발행 안 함. */
    public record QaAssigneeChangedEvent(Long qaItemId, Long projectId, String title,
                                         Long actorMemberId, Long assigneeMemberId) {}
}
