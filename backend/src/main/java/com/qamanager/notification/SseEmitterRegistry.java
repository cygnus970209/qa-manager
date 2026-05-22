package com.qamanager.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

@Component
public class SseEmitterRegistry {

    private static final Logger log = LoggerFactory.getLogger(SseEmitterRegistry.class);
    private static final long DEFAULT_TIMEOUT_MS = 30L * 60 * 1000; // 30분

    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter register(Long memberId) {
        SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT_MS);
        emitters.computeIfAbsent(memberId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(memberId, emitter));
        emitter.onTimeout(() -> remove(memberId, emitter));
        emitter.onError(t -> remove(memberId, emitter));
        try {
            emitter.send(SseEmitter.event().name("connected").data(Map.of("memberId", memberId)));
        } catch (IOException e) {
            log.warn("SSE 초기 메시지 전송 실패 memberId={}", memberId, e);
            remove(memberId, emitter);
        }
        return emitter;
    }

    public void publish(Long recipientId, String eventName, Object payload) {
        List<SseEmitter> list = emitters.get(recipientId);
        if (list == null) return;
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(payload));
            } catch (IOException e) {
                log.debug("SSE 전송 실패, emitter 제거 memberId={}", recipientId);
                remove(recipientId, emitter);
            }
        }
    }

    private void remove(Long memberId, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(memberId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) emitters.remove(memberId);
        }
    }
}
