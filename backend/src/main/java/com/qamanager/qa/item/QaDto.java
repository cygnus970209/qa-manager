package com.qamanager.qa.item;

import com.qamanager.member.TeamMember;
import com.qamanager.qa.shared.QaPriority;
import com.qamanager.qa.shared.QaStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public class QaDto {

    public record AssigneeSummary(Long id, String name, String avatarUrl) {
        public static AssigneeSummary of(TeamMember m) {
            return m == null ? null : new AssigneeSummary(m.getId(), m.getName(), m.getAvatarUrl());
        }
    }

    public record Response(
        Long id,
        Long updateId,
        String title,
        String description,
        String category,
        String status,
        AssigneeSummary tester,
        AssigneeSummary assignee1,
        AssigneeSummary assignee2,
        String priority,
        List<String> images,
        String createdAt,
        String updatedAt
    ) {
        public static Response from(QaItem q) {
            return new Response(
                q.getId(),
                q.getProjectUpdate().getId(),
                q.getTitle(),
                q.getDescription(),
                q.getCategory(),
                q.getStatus().getCode(),
                AssigneeSummary.of(q.getTester()),
                AssigneeSummary.of(q.getAssignee1()),
                AssigneeSummary.of(q.getAssignee2()),
                q.getPriority().getCode(),
                q.getImages().stream().map(QaItemImage::getImageUrl).toList(),
                q.getCreatedAt() != null ? q.getCreatedAt().toString() : null,
                q.getUpdatedAt() != null ? q.getUpdatedAt().toString() : null
            );
        }
    }

    public record CreateRequest(
        @NotNull Long updateId,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 4000) String description,
        @Size(max = 50) String category,
        @NotNull QaStatus status,
        /** null 이면 현재 로그인 사용자를 tester 로 자동 지정. */
        Long testerId,
        Long assignee1Id,
        Long assignee2Id,
        @NotNull QaPriority priority,
        List<@Size(max = 800) String> images
    ) {}

    public record UpdateRequest(
        @Size(max = 200) String title,
        @Size(max = 4000) String description,
        @Size(max = 50) String category,
        QaStatus status,
        Long testerId,
        Long assignee1Id,
        Long assignee2Id,
        QaPriority priority,
        List<@Size(max = 800) String> images,
        /** 명시적으로 비우려면 true. (null 인 채로 보내면 '변경 없음' 으로 간주) */
        Boolean clearTester,
        Boolean clearAssignee1,
        Boolean clearAssignee2
    ) {}

    public record HistoryResponse(
        Long id,
        String field,
        String oldValue,
        String newValue,
        AssigneeSummary changedBy,
        String changedAt
    ) {
        public static HistoryResponse from(QaHistory h) {
            return new HistoryResponse(
                h.getId(), h.getField(), h.getOldValue(), h.getNewValue(),
                AssigneeSummary.of(h.getChangedBy()),
                h.getChangedAt() != null ? h.getChangedAt().toString() : null
            );
        }
    }
}
