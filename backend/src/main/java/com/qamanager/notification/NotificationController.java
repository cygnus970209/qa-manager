package com.qamanager.notification;

import com.qamanager.auth.CurrentUser;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final SseEmitterRegistry sseRegistry;

    public NotificationController(NotificationService notificationService, SseEmitterRegistry sseRegistry) {
        this.notificationService = notificationService;
        this.sseRegistry = sseRegistry;
    }

    @GetMapping
    public List<NotificationDto.Response> listMine() {
        return notificationService.listMine(CurrentUser.getIdOrThrow());
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount() {
        return Map.of("count", notificationService.unreadCount(CurrentUser.getIdOrThrow()));
    }

    @PatchMapping("/{id}/read")
    public NotificationDto.Response markRead(@PathVariable Long id) {
        return notificationService.markRead(id, CurrentUser.getIdOrThrow());
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return sseRegistry.register(CurrentUser.getIdOrThrow());
    }
}
