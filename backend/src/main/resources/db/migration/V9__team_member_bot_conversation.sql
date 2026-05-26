-- Teams 알림: Graph chat 메세징 -> Bot Framework 프로액티브 메세징 전환.
-- teams_chat_id 의 의미가 "Graph oneOnOne chat id" -> "Bot Framework conversation id" 로 바뀐다 (컬럼 재사용).
-- teams_service_url : 봇 설치(conversationUpdate) 이벤트에서 캐시한 Bot Connector serviceUrl.
--   캐시가 없으면 글로벌 기본값(app.teams.service-url-default)으로 발송한다.

ALTER TABLE team_member
    ADD COLUMN teams_service_url VARCHAR(255) NULL AFTER teams_chat_id;
