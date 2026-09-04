-- 통합 검색 인덱스. QA · 코멘트 · 프로젝트 · 업데이트 · 테스트 케이스를 한 테이블에 모아 한 번의 쿼리로 찾는다.
-- 원본 테이블은 건드리지 않고, 엔티티가 바뀔 때(JPA 리스너, 커밋 후) 해당 행만 다시 쓴다.
--
-- ngram_text: 한국어 중간 글자 매칭을 위해 앱이 만든 바이그램 토큰("결제 화면" → "결제0 제_0? ..." 형식은 SearchTokenizer 참고).
--   MariaDB InnoDB 전문검색은 단어 단위라 "카드결제"에서 "결제"를 못 찾는다. 토큰 뒤에 '0' 을 붙여 3글자로 만들어
--   기본 최소 토큰 길이(innodb_ft_min_token_size=3)와 영문 불용어 목록에 걸리지 않게 한다 → DB 설정 변경 불필요.
-- search_text: 제목+본문 소문자본 (제목 일치 가산점·발췌용).
CREATE TABLE search_document (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    entity_type     VARCHAR(20)  NOT NULL,   -- qa | comment | project | update | test_case
    entity_id       BIGINT       NOT NULL,
    project_id      BIGINT       NULL,
    update_id       BIGINT       NULL,
    qa_item_id      BIGINT       NULL,
    title           VARCHAR(500) NOT NULL,
    body            TEXT         NULL,
    search_text     MEDIUMTEXT   NOT NULL,
    ngram_text      MEDIUMTEXT   NOT NULL,
    status          VARCHAR(30)  NULL,
    updated_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_search_entity (entity_type, entity_id),
    KEY ix_search_project (project_id),
    KEY ix_search_update (update_id),
    KEY ix_search_qa (qa_item_id),
    FULLTEXT KEY ft_search_ngram (ngram_text)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
