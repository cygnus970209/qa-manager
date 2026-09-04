package com.qamanager.search;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 검색 인덱스 유지.
 * - 앱 시작 시 인덱스가 비어 있으면(첫 배포·수동 초기화) 백그라운드에서 전체를 채운다.
 *   기동을 막지 않도록 별도 스레드에서 돌리고, 실패해도 앱은 뜬다 (관리자 화면에서 다시 만들 수 있다).
 * - 매일 04:00 전체를 다시 만든다 — 커밋 후 갱신이 일시적으로 실패했거나(DB 순단) SQL 로 직접 고친 데이터가 있어도
 *   하루 안에 원본과 다시 맞춰지는 안전망. 몇천 건 기준 수 초라 부담이 없다.
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
                indexService.reindexAll("startup");
            } catch (RuntimeException e) {
                log.warn("검색 인덱스 초기 생성 실패 — 설정 > 검색 인덱스에서 다시 만들 수 있습니다: {}", e.toString());
            }
        }, "search-index-bootstrap");
        t.setDaemon(true);
        t.start();
    }

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void nightlyReindex() {
        try {
            indexService.reindexAll("schedule");
        } catch (RuntimeException e) {
            // 파랑/초록이 잠시 겹쳐 둘 다 돌면 한쪽이 유니크 키에 걸릴 수 있다 — 다른 쪽이 완료하므로 무시
            log.warn("검색 인덱스 야간 재생성 실패: {}", e.toString());
        }
    }
}
