-- Bot Framework conversation id 는 Graph chat id 보다 훨씬 길다.
-- 개인 1:1 conversation 의 "a:..." 형식은 150자를 넘기므로 VARCHAR(128) 로는 잘린다(Data too long).
-- 여유있게 512 로 확장.

ALTER TABLE team_member
    MODIFY COLUMN teams_chat_id VARCHAR(512) NULL;
