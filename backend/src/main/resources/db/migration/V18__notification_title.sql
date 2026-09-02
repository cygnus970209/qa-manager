-- 알림 제목(QA 제목 스냅샷).
-- 알림센터 목록의 첫 줄, 데스크톱 네이티브 알림의 제목, Teams 카드의 제목으로 쓴다.
-- message 는 본문(예: "새 코멘트가 달렸습니다: <댓글 발췌>")만 담는다.
-- 기존 행은 NULL — 클라이언트는 title 이 없으면 message 만 표시한다.
ALTER TABLE notification
    ADD COLUMN title VARCHAR(200) NULL AFTER type;
