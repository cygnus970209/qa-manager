package com.qamanager.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class SseEmitterRegistry implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(SseEmitterRegistry.class);
    private static final long DEFAULT_TIMEOUT_MS = 30L * 60 * 1000; // 30분
    /**
     * keep-alive 간격. 프록시 유휴 타임아웃(nginx 기본 60s)보다 짧게 잡아 조용한 연결이 끊기지 않게 하고,
     * 클라이언트(frontend notifications 스토어)는 이 코멘트가 90s 동안 없으면 죽은 연결로 보고 재연결한다.
     */
    private static final long HEARTBEAT_MS = 25_000;

    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "sse-heartbeat");
        t.setDaemon(true);
        return t;
    });

    public SseEmitterRegistry() {
        heartbeat.scheduleAtFixedRate(this::sendHeartbeat, HEARTBEAT_MS, HEARTBEAT_MS, TimeUnit.MILLISECONDS);
    }

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

    /** 모든 연결에 SSE 코멘트(":keep-alive")를 보낸다. 클라이언트 파서는 코멘트를 무시한다. */
    private void sendHeartbeat() {
        try {
            emitters.forEach((memberId, list) -> {
                for (SseEmitter emitter : list) {
                    try {
                        emitter.send(SseEmitter.event().comment("keep-alive"));
                    } catch (IOException | IllegalStateException e) {
                        // 끊긴 연결 / 이미 완료된 emitter → 정리
                        remove(memberId, emitter);
                    }
                }
            });
        } catch (RuntimeException e) {
            // scheduleAtFixedRate 는 예외가 새면 이후 실행이 멈추므로 여기서 막는다
            log.warn("SSE keep-alive 전송 중 오류", e);
        }
    }

    private void remove(Long memberId, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(memberId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) emitters.remove(memberId);
        }
    }

    @Override
    public void destroy() {
        heartbeat.shutdownNow();
    }
}
