package com.qamanager.projectupdate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class UpdateDto {

    public record Response(
        Long id,
        Long projectId,
        String version,
        String title,
        String description,
        String status,
        String createdAt,
        String updatedAt
    ) {
        public static Response from(ProjectUpdate u) {
            return new Response(
                u.getId(),
                u.getProject().getId(),
                u.getVersion(),
                u.getTitle(),
                u.getDescription(),
                u.getStatus().getCode(),
                u.getCreatedAt() != null ? u.getCreatedAt().toString() : null,
                u.getUpdatedAt() != null ? u.getUpdatedAt().toString() : null
            );
        }
    }

    public record CreateRequest(
        @NotBlank @Size(max = 50) String version,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 4000) String description,
        @NotNull UpdateStatus status
    ) {}

    public record UpdateRequest(
        @Size(max = 50) String version,
        @Size(max = 200) String title,
        @Size(max = 4000) String description,
        UpdateStatus status
    ) {}
}
