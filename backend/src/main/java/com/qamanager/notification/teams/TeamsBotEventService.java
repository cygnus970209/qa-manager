package com.qamanager.notification.teams;

import com.fasterxml.jackson.databind.JsonNode;
import com.qamanager.member.TeamMemberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bot Framework 인바운드 activity 처리.
 *
 * 사용자가 봇을 설치(conversationUpdate)하거나 봇에게 메세지(message)를 보낼 때,
 * 해당 사용자의 conversation id 와 serviceUrl 을 TeamMember 에 캐싱한다.
 * 이후 알림 발송은 캐시된 값을 우선 사용하므로 createConversation 호출과 글로벌 URL 의존을 줄인다.
 *
 * 매칭은 from.aadObjectId 를 이미 캐싱된 teams_user_id 와 대조한다.
 * (teams_user_id 가 아직 없는 멤버는 다음 발송 시 email->AAD 조회로 채워진 뒤 createConversation 으로 처리된다.)
 */
@Service
public class TeamsBotEventService {

    private static final Logger log = LoggerFactory.getLogger(TeamsBotEventService.class);

    private final TeamMemberRepository memberRepository;

    public TeamsBotEventService(TeamMemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Transactional
    public void handle(JsonNode activity) {
        if (activity == null) return;
        String type = activity.path("type").asText("");
        if (!"conversationUpdate".equals(type) && !"message".equals(type)) {
            return; // 그 외 이벤트는 무시
        }

        String aadObjectId = activity.path("from").path("aadObjectId").asText(null);
        String conversationId = activity.path("conversation").path("id").asText(null);
        String serviceUrl = activity.path("serviceUrl").asText(null);
        if (aadObjectId == null || aadObjectId.isBlank() || conversationId == null || conversationId.isBlank()) {
            return;
        }

        memberRepository.findByTeamsUserIdAndDeletedAtIsNull(aadObjectId).ifPresentOrElse(
            m -> {
                m.cacheConversation(conversationId, serviceUrl);
                log.info("Teams conversation 캐시 갱신 (member={}, type={})", m.getId(), type);
            },
            () -> log.debug("Teams 인바운드 이벤트의 aadObjectId 에 매칭되는 멤버 없음 (type={})", type)
        );
    }
}
