package com.qamanager.notification;

public class NotificationDto {

    public record Response(
        Long id,
        String type,
        String message,
        Long projectId,
        String projectName,
        Long qaItemId,
        Long actorId,
        String actorName,
        boolean read,
        String createdAt
    ) {
        public static Response from(Notification n) {
            return new Response(
                n.getId(),
                n.getType(),
                n.getMessage(),
                n.getProject() == null ? null : n.getProject().getId(),
                n.getProject() == null ? null : n.getProject().getName(),
                n.getQaItem() == null ? null : n.getQaItem().getId(),
                n.getActor() == null ? null : n.getActor().getId(),
                n.getActor() == null ? null : n.getActor().getName(),
                n.isRead(),
                n.getCreatedAt() != null ? n.getCreatedAt().toString() : null
            );
        }
    }
}
