package com.qamanager.search;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 앱 시작 시 검색 인덱스가 비어 있으면(첫 배포·수동 초기화) 백그라운드에서 전체를 채운다.
 * 기동을 막지 않도록 별도 스레드에서 돌리고, 실패해도 앱은 뜬다 (관리자 화면에서 다시 만들 수 있다).
 */
@Component
public class SearchIndexBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SearchIndexBootstrap.class);

    private final SearchIndexService indexService;

    public SearchIndexBootstrap(SearchIndexService indexService) {
        this.indexService = indexService;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean empty;
        try {
            empty = indexService.isEmpty();
        } catch (RuntimeException e) {
            log.warn("검색 인덱스 상태 확인 실패: {}", e.toString());
            return;
        }
        if (!empty) return;
        Thread t = new Thread(() -> {
            try {
                indexService.reindexAll();
            } catch (RuntimeException e) {
                log.warn("검색 인덱스 초기 생성 실패 — 설정 > 검색 인덱스에서 다시 만들 수 있습니다: {}", e.toString());
            }
        }, "search-index-bootstrap");
        t.setDaemon(true);
        t.start();
    }
}
