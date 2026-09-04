-- 검색 문서 내용 해시 (제목·본문·상태·소속의 SHA-256). 관리자 "색인 상태 검사"가 원본과 대조해
-- 내용이 바뀌었는데 갱신되지 않은 문서(stale)를 찾는 데 쓴다. 기존 행은 NULL → 검사에서 stale 로 보고 다음 재색인 때 채워진다.
ALTER TABLE search_document
    ADD COLUMN content_hash CHAR(64) NULL AFTER status;
