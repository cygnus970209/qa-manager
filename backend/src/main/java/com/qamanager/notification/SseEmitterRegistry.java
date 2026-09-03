package com.qamanager.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.SmartLifecycle;
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
public class SseEmitterRegistry implements DisposableBean, SmartLifecycle {

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

    /* ─── 종료 순서 (무중단 배포) ───
     * server.shutdown=graceful 은 처리 중인 요청이 끝날 때까지 기다리는데, SSE 스트림은 끝나지 않는 요청이라
     * 그대로 두면 종료가 timeout(30초)까지 늘어진다. 그래서 웹 서버 graceful 단계(phase DEFAULT_PHASE-1024)보다
     * 먼저 멈추는 phase 에서 모든 emitter 를 닫는다. 클라이언트는 1초 뒤 재연결해 새 인스턴스(다른 색)로 붙는다. */
    private volatile boolean running = false;

    @Override
    public void start() {
        running = true;
    }

    @Override
    public void stop() {
        running = false;
        int closed = 0;
        for (List<SseEmitter> list : emitters.values()) {
            for (SseEmitter emitter : list) {
                try {
                    emitter.complete();
                    closed++;
                } catch (RuntimeException ignored) {
                    // 이미 끊긴 연결
                }
            }
        }
        emitters.clear();
        if (closed > 0) log.info("종료 전 SSE 연결 {}개 닫음 — 클라이언트가 새 인스턴스로 재연결", closed);
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        // 높은 phase 가 먼저 멈춘다 — 웹 서버 graceful shutdown 보다 앞서 SSE 를 정리
        return DEFAULT_PHASE;
    }
}
