package com.qamanager.qa.item;

import com.qamanager.common.ApiException;
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
import java.util.List;
import java.util.Objects;

@Service
public class QaItemService {

    private final QaItemRepository qaRepository;
    private final QaHistoryRepository historyRepository;
    private final ProjectUpdateRepository updateRepository;
    private final TeamMemberRepository memberRepository;
    private final ApplicationEventPublisher events;

    public QaItemService(QaItemRepository qaRepository,
                         QaHistoryRepository historyRepository,
                         ProjectUpdateRepository updateRepository,
                         TeamMemberRepository memberRepository,
                         ApplicationEventPublisher events) {
        this.qaRepository = qaRepository;
        this.historyRepository = historyRepository;
        this.updateRepository = updateRepository;
        this.memberRepository = memberRepository;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<QaDto.Response> list(Long updateId, String status, String priority, Long assigneeId) {
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
            spec = spec.and((root, q, cb) -> cb.equal(root.get("assignee").get("id"), assigneeId));
        }
        spec = spec.and((root, q, cb) -> {
            q.orderBy(cb.desc(root.get("createdAt")));
            return cb.conjunction();
        });
        return qaRepository.findAll(spec).stream().map(QaDto.Response::from).toList();
    }

    @Transactional(readOnly = true)
    public QaDto.Response get(Long id) {
        return QaDto.Response.from(findOrThrow(id));
    }

    @Transactional
    public QaDto.Response create(QaDto.CreateRequest req, Long currentMemberId) {
        ProjectUpdate update = updateRepository.findById(req.updateId())
            .orElseThrow(() -> ApiException.notFound("업데이트를 찾을 수 없습니다. id=" + req.updateId()));
        TeamMember assignee = resolveAssignee(req.assigneeId(), false);

        QaItem item = new QaItem(update, req.title(), req.description(), req.category(),
            req.status(), assignee, req.priority());
        item.replaceImages(req.images());
        QaItem saved = qaRepository.save(item);

        events.publishEvent(new QaCreatedEvent(saved.getId(), update.getProject().getId(),
            saved.getTitle(), currentMemberId, assignee == null ? null : assignee.getId()));
        return QaDto.Response.from(saved);
    }

    @Transactional
    public QaDto.Response update(Long id, QaDto.UpdateRequest req, Long currentMemberId) {
        QaItem q = findOrThrow(id);
        TeamMember changedBy = memberRepository.findById(currentMemberId).orElse(null);
        List<QaHistory> diffs = new ArrayList<>();

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
                q.getAssignee() == null ? null : q.getAssignee().getId()));
        }
        if (req.priority() != null && req.priority() != q.getPriority()) {
            diffs.add(new QaHistory(q, "priority", q.getPriority().getCode(), req.priority().getCode(), changedBy));
            q.setPriority(req.priority());
        }
        if (Boolean.TRUE.equals(req.clearAssignee())) {
            if (q.getAssignee() != null) {
                diffs.add(new QaHistory(q, "assignee", q.getAssignee().getName(), null, changedBy));
                q.setAssignee(null);
            }
        } else if (req.assigneeId() != null) {
            Long currentId = q.getAssignee() == null ? null : q.getAssignee().getId();
            if (!Objects.equals(currentId, req.assigneeId())) {
                TeamMember next = resolveAssignee(req.assigneeId(), true);
                diffs.add(new QaHistory(q, "assignee",
                    q.getAssignee() == null ? null : q.getAssignee().getName(),
                    next.getName(), changedBy));
                q.setAssignee(next);
                events.publishEvent(new QaAssigneeChangedEvent(
                    q.getId(), q.getProjectUpdate().getProject().getId(),
                    q.getTitle(), currentMemberId, next.getId()
                ));
            }
        }
        if (req.images() != null) {
            q.replaceImages(req.images());
        }
        if (!diffs.isEmpty()) historyRepository.saveAll(diffs);
        return QaDto.Response.from(q);
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

    private TeamMember resolveAssignee(Long id, boolean required) {
        if (id == null) {
            if (required) throw ApiException.badRequest("assigneeId 가 필요합니다.");
            return null;
        }
        return memberRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> ApiException.notFound("담당자를 찾을 수 없습니다. id=" + id));
    }

    private String truncate(String s) {
        if (s == null) return null;
        return s.length() <= 500 ? s : s.substring(0, 500);
    }

    /* ─────── 이벤트 (Notification 도메인에서 구독) ─────── */
    public record QaCreatedEvent(Long qaItemId, Long projectId, String title,
                                 Long actorMemberId, Long assigneeMemberId) {}

    public record QaStatusChangedEvent(Long qaItemId, Long projectId, String title,
                                       String newStatus, Long actorMemberId, Long assigneeMemberId) {}

    /** assignee 가 비어있다가 지정되거나 다른 사람으로 변경됐을 때 */
    public record QaAssigneeChangedEvent(Long qaItemId, Long projectId, String title,
                                         Long actorMemberId, Long assigneeMemberId) {}
}
