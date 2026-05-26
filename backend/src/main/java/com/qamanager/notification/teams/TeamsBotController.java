package com.qamanager.notification.teams;

import tools.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bot Framework messaging endpoint. Azure Bot 의 Messaging endpoint 로 등록한다
 * (https://&lt;도메인&gt;/api/teams/messages).
 *
 * SecurityFilterChain 에서 permitAll 이므로 {@link TeamsBotJwtValidator} 검증이 유일한 인증이다.
 * 봇 설치/메세지 이벤트를 받아 conversation 정보를 캐싱한다(발송은 별도 경로).
 */
@RestController
@RequestMapping("/api/teams")
public class TeamsBotController {

    private static final Logger log = LoggerFactory.getLogger(TeamsBotController.class);

    private final TeamsProperties props;
    private final TeamsBotJwtValidator jwtValidator;
    private final TeamsBotEventService eventService;

    public TeamsBotController(TeamsProperties props,
                             TeamsBotJwtValidator jwtValidator,
                             TeamsBotEventService eventService) {
        this.props = props;
        this.jwtValidator = jwtValidator;
        this.eventService = eventService;
    }

    @PostMapping("/messages")
    public ResponseEntity<Void> messages(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @RequestBody JsonNode activity) {

        if (!props.isUsable()) {
            return ResponseEntity.status(503).build(); // Teams 비활성 상태
        }
        try {
            String activityServiceUrl = activity == null ? null : activity.path("serviceUrl").asText(null);
            jwtValidator.validate(authorization, activityServiceUrl);
        } catch (TeamsApiException e) {
            log.warn("Bot 요청 인증 실패: {}", e.getMessage());
            return ResponseEntity.status(401).build();
        }

        try {
            eventService.handle(activity);
        } catch (Exception e) {
            // 이벤트 처리 실패가 봇 채널에 영향 주지 않도록 swallow (200 반환)
            log.warn("Bot 인바운드 이벤트 처리 실패: {}", e.getMessage());
        }
        return ResponseEntity.ok().build();
    }
}
