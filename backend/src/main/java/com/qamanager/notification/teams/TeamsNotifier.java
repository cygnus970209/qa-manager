package com.qamanager.notification.teams;

import com.qamanager.member.TeamMember;
import com.qamanager.member.TeamMemberRepository;
import com.qamanager.notification.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Notification 이 DB 에 저장된 후 Teams 1:1 메세지를 발송한다.
 *
 * 정책:
 * - 메인 트랜잭션과 분리 (@Async + 새 트랜잭션). Teams 실패가 DB/SSE 알림에 영향 없도록.
 * - 발송 불가능 (설정 없음 / email 없음 / 사용자 토글 off) 이면 silent skip.
 * - chat id 는 DB 에 캐시. 처음 발송 시 생성.
 * - 모든 예외는 잡아서 로그만. 재시도 큐는 없음 (단순화). 필요시 추후 추가.
 */
@Component
public class TeamsNotifier {

    private static final Logger log = LoggerFactory.getLogger(TeamsNotifier.class);

    private final TeamsGraphClient client;
    private final TeamMemberRepository memberRepository;
    private final String linkBaseUrl;

    public TeamsNotifier(TeamsGraphClient client,
                         TeamMemberRepository memberRepository,
                         @Value("${app.cors.allowed-origins:}") String allowedOrigins) {
        this.client = client;
        this.memberRepository = memberRepository;
        // 알림 클릭 시 이동할 프론트 URL 의 base. allowed-origins 의 첫 항목을 기본값으로.
        this.linkBaseUrl = firstOrigin(allowedOrigins);
    }

    /**
     * 비동기 발송 진입점.
     * notification 은 detached 상태일 수 있으므로 ID 와 핵심 필드만 받는다.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void send(Long recipientId, NotificationPayload payload) {
        if (!client.isUsable()) return;
        try {
            TeamMember recipient = memberRepository.findByIdAndDeletedAtIsNull(recipientId).orElse(null);
            if (recipient == null) return;
            if (!recipient.isTeamsNotifyEnabled()) return;
            if (recipient.getEmail() == null || recipient.getEmail().isBlank()) return;

            String teamsUserId = recipient.getTeamsUserId();
            if (teamsUserId == null) {
                teamsUserId = client.findUserIdByEmail(recipient.getEmail()).orElse(null);
                if (teamsUserId == null) {
                    log.info("Teams 사용자 매핑 실패: email={} (member={})", recipient.getEmail(), recipientId);
                    return;
                }
                recipient.linkTeamsUser(teamsUserId);
            }

            String chatId = recipient.getTeamsChatId();
            if (chatId == null) {
                chatId = client.createOneOnOneChat(teamsUserId);
                recipient.cacheTeamsChat(chatId);
            }

            Map<String, Object> card = buildCard(payload);
            client.sendAdaptiveCard(chatId, payload.message(), card);
        } catch (TeamsApiException e) {
            log.warn("Teams 알림 발송 실패 (member={}): {}", recipientId, e.getMessage());
        } catch (Exception e) {
            log.error("Teams 알림 발송 중 예기치 못한 오류 (member={})", recipientId, e);
        }
    }

    /* ─────────────── Adaptive Card ─────────────── */

    private Map<String, Object> buildCard(NotificationPayload p) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "TextBlock");
        body.put("text", p.message());
        body.put("wrap", true);
        body.put("weight", "Bolder");

        Map<String, Object> sub = new LinkedHashMap<>();
        sub.put("type", "TextBlock");
        StringBuilder subText = new StringBuilder();
        if (p.projectName() != null) subText.append(p.projectName());
        if (p.actorName() != null) {
            if (!subText.isEmpty()) subText.append(" · ");
            subText.append(p.actorName());
        }
        sub.put("text", subText.toString());
        sub.put("wrap", true);
        sub.put("isSubtle", true);
        sub.put("spacing", "Small");

        Map<String, Object> card = new LinkedHashMap<>();
        card.put("type", "AdaptiveCard");
        card.put("$schema", "http://adaptivecards.io/schemas/adaptive-card.json");
        card.put("version", "1.4");
        card.put("body", List.of(body, sub));

        String deepLink = buildDeepLink(p);
        if (deepLink != null) {
            card.put("actions", List.of(Map.of(
                "type", "Action.OpenUrl",
                "title", "QA 열기",
                "url", deepLink
            )));
        }
        return card;
    }

    private String buildDeepLink(NotificationPayload p) {
        if (linkBaseUrl == null || linkBaseUrl.isBlank()) return null;
        if (p.qaItemId() != null) {
            return linkBaseUrl + "/qa/" + p.qaItemId();
        }
        if (p.projectId() != null) {
            return linkBaseUrl + "/projects/" + p.projectId();
        }
        return null;
    }

    private static String firstOrigin(String csv) {
        if (csv == null) return null;
        for (String s : csv.split(",")) {
            String t = s.trim();
            if (!t.isEmpty()) return t;
        }
        return null;
    }

    /** Notification 에서 필요한 정보만 추려서 비동기로 넘기는 DTO. */
    public record NotificationPayload(
        String type,
        String message,
        Long projectId,
        String projectName,
        Long qaItemId,
        String actorName
    ) {
        public static NotificationPayload from(Notification n) {
            return new NotificationPayload(
                n.getType(),
                n.getMessage(),
                n.getProject() == null ? null : n.getProject().getId(),
                n.getProject() == null ? null : n.getProject().getName(),
                n.getQaItem() == null ? null : n.getQaItem().getId(),
                n.getActor() == null ? null : n.getActor().getName()
            );
        }
    }
}
