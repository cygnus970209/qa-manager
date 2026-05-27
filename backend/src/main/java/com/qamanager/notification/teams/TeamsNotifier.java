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

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Notification 이 DB 에 저장된 후 Teams 1:1 메세지를 발송한다.
 *
 * 정책:
 * - 메인 트랜잭션과 분리 (@Async + 새 트랜잭션). Teams 실패가 DB/SSE 알림에 영향 없도록.
 * - 발송 불가능 (설정 없음 / email 없음 / 도메인 불일치 / 사용자 토글 off) 이면 SKIPPED 로그만 남기고 종료.
 * - chat id 는 DB 에 캐시. 처음 발송 시 생성.
 * - 모든 결과 (SENT/SKIPPED/FAILED) 는 teams_send_log 에 비동기 적재.
 */
@Component
public class TeamsNotifier {

    private static final Logger log = LoggerFactory.getLogger(TeamsNotifier.class);

    private final TeamsGraphClient client;
    private final TeamsBotClient botClient;
    private final TeamsProperties props;
    private final TeamMemberRepository memberRepository;
    private final TeamsSendLogService logService;
    private final String linkBaseUrl;

    public TeamsNotifier(TeamsGraphClient client,
                         TeamsBotClient botClient,
                         TeamsProperties props,
                         TeamMemberRepository memberRepository,
                         TeamsSendLogService logService,
                         @Value("${app.cors.allowed-origins:}") String allowedOrigins) {
        this.client = client;
        this.botClient = botClient;
        this.props = props;
        this.memberRepository = memberRepository;
        this.logService = logService;
        this.linkBaseUrl = firstOrigin(allowedOrigins);
    }

    /**
     * 비동기 발송 진입점.
     * notification 은 detached 상태일 수 있으므로 ID 와 핵심 필드만 받는다.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void send(Long recipientId, NotificationPayload payload) {
        long start = System.currentTimeMillis();
        if (!client.isUsable()) {
            logService.record(TeamsSendLog.STATUS_SKIPPED, payload.type(), recipientId, null,
                payload.message(), "Teams 설정 비활성화", elapsed(start));
            return;
        }
        TeamMember recipient = memberRepository.findByIdAndDeletedAtIsNull(recipientId).orElse(null);
        if (recipient == null) {
            logService.record(TeamsSendLog.STATUS_SKIPPED, payload.type(), recipientId, null,
                payload.message(), "멤버 없음", elapsed(start));
            return;
        }
        if (!recipient.isTeamsNotifyEnabled()) {
            logService.record(TeamsSendLog.STATUS_SKIPPED, payload.type(), recipientId, recipient.getEmail(),
                payload.message(), "수신자 알림 토글 off", elapsed(start));
            return;
        }
        if (!isTypeEnabled(recipient, payload.type())) {
            logService.record(TeamsSendLog.STATUS_SKIPPED, payload.type(), recipientId, recipient.getEmail(),
                payload.message(), "알림 종류 off (" + payload.type() + ")", elapsed(start));
            return;
        }
        if (isInQuietHours(recipient)) {
            logService.record(TeamsSendLog.STATUS_SKIPPED, payload.type(), recipientId, recipient.getEmail(),
                payload.message(), "방해금지 시간대", elapsed(start));
            return;
        }

        String email = recipient.resolveEmailCandidate();
        if (email == null) {
            logService.record(TeamsSendLog.STATUS_SKIPPED, payload.type(), recipientId, null,
                payload.message(), "email 후보 없음 (username 도 이메일 형식 아님)", elapsed(start));
            return;
        }
        if (!props.matchesEmailDomain(email)) {
            logService.record(TeamsSendLog.STATUS_SKIPPED, payload.type(), recipientId, email,
                payload.message(), "도메인 불일치 (allowed=" + props.emailDomain() + ")", elapsed(start));
            return;
        }

        try {
            String teamsUserId = recipient.getTeamsUserId();
            if (teamsUserId == null) {
                teamsUserId = client.findUserIdByEmail(email).orElse(null);
                if (teamsUserId == null) {
                    logService.record(TeamsSendLog.STATUS_SKIPPED, payload.type(), recipientId, email,
                        payload.message(), "AAD 사용자 없음", elapsed(start));
                    return;
                }
                recipient.linkTeamsUser(teamsUserId);
            }

            String conversationId = recipient.getTeamsChatId();
            String serviceUrl = recipient.getTeamsServiceUrl();
            if (conversationId == null) {
                serviceUrl = props.effectiveServiceUrlDefault();
                conversationId = botClient.createConversation(teamsUserId, serviceUrl);
                recipient.cacheConversation(conversationId, serviceUrl);
            }

            Map<String, Object> card = buildCard(payload);
            botClient.sendAdaptiveCard(serviceUrl, conversationId, payload.message(), card);
            logService.record(TeamsSendLog.STATUS_SENT, payload.type(), recipientId, email,
                payload.message(), null, elapsed(start));
        } catch (TeamsApiException e) {
            log.warn("Teams 알림 발송 실패 (member={}): {}", recipientId, e.getMessage());
            logService.record(TeamsSendLog.STATUS_FAILED, payload.type(), recipientId, email,
                payload.message(), failDetail(e), elapsed(start));
        } catch (Exception e) {
            log.error("Teams 알림 발송 중 예기치 못한 오류 (member={})", recipientId, e);
            logService.record(TeamsSendLog.STATUS_FAILED, payload.type(), recipientId, email,
                payload.message(), e.getClass().getSimpleName() + ": " + e.getMessage(), elapsed(start));
        }
    }

    /**
     * 진단 정보를 포함한 동기 테스트 발송. UI 의 "테스트 발송" 버튼이 호출한다.
     * 실패해도 예외 던지지 않고 결과 객체에 상세 사유를 담아 반환.
     */
    @Transactional
    public TestSendResult testSend(Long recipientId) {
        long start = System.currentTimeMillis();
        TestSendResult.Builder b = TestSendResult.builder();

        if (!client.isUsable()) {
            TestSendResult r = b.fail("Teams 설정이 활성화되지 않았습니다. 환경변수(TEAMS_ENABLED, TENANT/CLIENT/SECRET, BOT_USER_OID)를 확인하세요.").build();
            logService.record(TeamsSendLog.STATUS_SKIPPED, "test", recipientId, null, null, r.errorMessage(), elapsed(start));
            return r;
        }
        b.configOk(true);

        TeamMember recipient = memberRepository.findByIdAndDeletedAtIsNull(recipientId).orElse(null);
        if (recipient == null) {
            TestSendResult r = b.fail("멤버를 찾을 수 없습니다 (id=" + recipientId + ").").build();
            logService.record(TeamsSendLog.STATUS_SKIPPED, "test", recipientId, null, null, r.errorMessage(), elapsed(start));
            return r;
        }
        b.memberName(recipient.getName());

        String email = recipient.resolveEmailCandidate();
        if (email == null) {
            TestSendResult r = b.fail("이 멤버에 사용 가능한 email 이 없습니다. 등록된 email 또는 username 이 이메일 형식이어야 합니다.").build();
            logService.record(TeamsSendLog.STATUS_SKIPPED, "test", recipientId, null, null, r.errorMessage(), elapsed(start));
            return r;
        }
        b.email(email);

        if (!props.matchesEmailDomain(email)) {
            TestSendResult r = b.fail("도메인이 허용 목록과 일치하지 않습니다. allowed=" + props.emailDomain()).build();
            logService.record(TeamsSendLog.STATUS_SKIPPED, "test", recipientId, email, null, r.errorMessage(), elapsed(start));
            return r;
        }

        if (!recipient.isTeamsNotifyEnabled()) {
            TestSendResult r = b.fail("이 멤버가 Teams 알림을 비활성화했습니다.").build();
            logService.record(TeamsSendLog.STATUS_SKIPPED, "test", recipientId, email, null, r.errorMessage(), elapsed(start));
            return r;
        }
        b.notifyEnabled(true);

        // AAD 매핑
        String teamsUserId = recipient.getTeamsUserId();
        try {
            if (teamsUserId == null) {
                teamsUserId = client.findUserIdByEmail(email).orElse(null);
                if (teamsUserId == null) {
                    TestSendResult r = b.fail("Azure AD 에서 사용자를 찾지 못했습니다 (email=" + email + ").").build();
                    logService.record(TeamsSendLog.STATUS_SKIPPED, "test", recipientId, email, null, r.errorMessage(), elapsed(start));
                    return r;
                }
                recipient.linkTeamsUser(teamsUserId);
            }
            b.aadMapped(true).teamsUserId(teamsUserId);
        } catch (TeamsApiException e) {
            TestSendResult r = b.fail("AAD 사용자 조회 실패: " + e.getMessage()).build();
            logService.record(TeamsSendLog.STATUS_FAILED, "test", recipientId, email, null, r.errorMessage(), elapsed(start));
            return r;
        }

        // conversation 캐시 / 생성
        String conversationId = recipient.getTeamsChatId();
        String serviceUrl = recipient.getTeamsServiceUrl();
        try {
            if (conversationId == null) {
                serviceUrl = props.effectiveServiceUrlDefault();
                conversationId = botClient.createConversation(teamsUserId, serviceUrl);
                recipient.cacheConversation(conversationId, serviceUrl);
            }
            b.chatOk(true).chatId(conversationId);
        } catch (TeamsApiException e) {
            TestSendResult r = b.fail("conversation 생성 실패: " + failDetail(e)).build();
            logService.record(TeamsSendLog.STATUS_FAILED, "test", recipientId, email, null, r.errorMessage(), elapsed(start));
            return r;
        }

        // 발송
        String testMessage = "[테스트] Teams 알림이 정상적으로 연결되었습니다.";
        try {
            Map<String, Object> card = buildCard(new NotificationPayload(
                "test", testMessage, null, null, null, null
            ));
            botClient.sendAdaptiveCard(serviceUrl, conversationId, "Teams 알림 테스트", card);
            TestSendResult r = b.sent(true).build();
            logService.record(TeamsSendLog.STATUS_SENT, "test", recipientId, email, testMessage, null, elapsed(start));
            return r;
        } catch (TeamsApiException e) {
            TestSendResult r = b.fail("메세지 발송 실패: " + failDetail(e)).build();
            logService.record(TeamsSendLog.STATUS_FAILED, "test", recipientId, email, testMessage, failDetail(e), elapsed(start));
            return r;
        }
    }

    /** 봇 미설치 케이스는 운영자가 바로 이해하도록 안내 문구로 변환. */
    private static String failDetail(TeamsApiException e) {
        if (e.isBotNotInstalled()) {
            return "봇이 사용자 Teams 에 설치되지 않았거나 차단됨 — 사용자가 봇 앱을 추가해야 발송됩니다. (" + e.getMessage() + ")";
        }
        return e.getMessage();
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

    private static int elapsed(long startMs) {
        return (int) (System.currentTimeMillis() - startMs);
    }

    /** 알림 종류(qa/comment/reply)별 멤버 토글. 알 수 없는 type(test 등)은 항상 허용. */
    private static boolean isTypeEnabled(TeamMember m, String type) {
        if (type == null) return true;
        return switch (type) {
            case "qa" -> m.isNotifyQaEnabled();
            case "comment" -> m.isNotifyCommentEnabled();
            case "reply" -> m.isNotifyReplyEnabled();
            default -> true;
        };
    }

    /** 현재 시각(Asia/Seoul)이 멤버의 방해금지 구간 내인지. 미설정/파싱실패는 false(발송). */
    private static boolean isInQuietHours(TeamMember m) {
        String s = m.getQuietHoursStart();
        String e = m.getQuietHoursEnd();
        if (s == null || s.isBlank() || e == null || e.isBlank()) return false;
        try {
            LocalTime start = LocalTime.parse(s);
            LocalTime end = LocalTime.parse(e);
            if (start.equals(end)) return false; // 빈 구간
            LocalTime now = ZonedDateTime.now(ZoneId.of("Asia/Seoul")).toLocalTime();
            if (start.isBefore(end)) {
                return !now.isBefore(start) && now.isBefore(end);        // [start, end)
            }
            return !now.isBefore(start) || now.isBefore(end);            // 자정 넘김 (예: 22:00~08:00)
        } catch (RuntimeException ex) {
            return false; // 잘못된 값이면 누락 방지를 위해 발송
        }
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
