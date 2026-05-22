package com.qamanager.qa.comment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public class CommentDto {

    public record AuthorSummary(Long id, String name, String avatarUrl) {}

    public record Response(
        Long id,
        Long qaItemId,
        Long parentId,
        AuthorSummary author,
        String content,
        List<String> images,
        /** emoji → 누른 member id 목록 */
        Map<String, List<Long>> reactions,
        String createdAt,
        String updatedAt
    ) {}

    public record CreateRequest(
        Long parentId,
        @NotBlank String content,
        List<@Size(max = 800) String> images
    ) {}

    public record UpdateRequest(
        @NotBlank String content,
        List<@Size(max = 800) String> images
    ) {}

    public record ReactionRequest(
        @NotBlank @Size(max = 20) String emoji
    ) {}
}
