package com.qamanager.qa.item;

import com.qamanager.qa.shared.QaPriority;
import com.qamanager.qa.shared.QaStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public class QaDto {

    public record AssigneeSummary(Long id, String name, String avatarUrl) {}

    public record Response(
        Long id,
        Long updateId,
        String title,
        String description,
        String category,
        String status,
        AssigneeSummary assignee,
        String priority,
        List<String> images,
        String createdAt,
        String updatedAt
    ) {
        public static Response from(QaItem q) {
            AssigneeSummary a = q.getAssignee() == null ? null : new AssigneeSummary(
                q.getAssignee().getId(), q.getAssignee().getName(), q.getAssignee().getAvatarUrl()
            );
            return new Response(
                q.getId(),
                q.getProjectUpdate().getId(),
                q.getTitle(),
                q.getDescription(),
                q.getCategory(),
                q.getStatus().getCode(),
                a,
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
        Long assigneeId,
        @NotNull QaPriority priority,
        List<@Size(max = 800) String> images
    ) {}

    public record UpdateRequest(
        @Size(max = 200) String title,
        @Size(max = 4000) String description,
        @Size(max = 50) String category,
        QaStatus status,
        Long assigneeId,
        QaPriority priority,
        List<@Size(max = 800) String> images,
        /** assigneeId 를 null로 명시 비우려면 clearAssignee=true */
        Boolean clearAssignee
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
            AssigneeSummary by = h.getChangedBy() == null ? null : new AssigneeSummary(
                h.getChangedBy().getId(), h.getChangedBy().getName(), h.getChangedBy().getAvatarUrl()
            );
            return new HistoryResponse(
                h.getId(), h.getField(), h.getOldValue(), h.getNewValue(),
                by, h.getChangedAt() != null ? h.getChangedAt().toString() : null
            );
        }
    }
}
