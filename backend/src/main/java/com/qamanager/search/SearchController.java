package com.qamanager.search;

import com.qamanager.auth.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchService searchService;
    private final SearchIndexService indexService;

    public SearchController(SearchService searchService, SearchIndexService indexService) {
        this.searchService = searchService;
        this.indexService = indexService;
    }

    /**
     * 통합 검색.
     * @param q         질의어 (2글자 이상 권장, "#123" 은 QA 번호)
     * @param types     qa,comment,project,update,test_case 중 콤마 구분 (없으면 전체)
     * @param projectId 프로젝트로 좁히기
     */
    @GetMapping
    public SearchDto.Response search(@RequestParam(defaultValue = "") String q,
                                     @RequestParam(required = false) List<String> types,
                                     @RequestParam(required = false) Long projectId,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "20") int size) {
        int safeSize = Math.min(Math.max(size, 1), 50);
        return searchService.search(q, types, projectId, Math.max(page, 0), safeSize);
    }

    /** 인덱스 현황 (관리자) */
    @GetMapping("/stats")
    public SearchDto.Stats stats() {
        CurrentUser.requireAdmin();
        Map<String, Long> counts = indexService.counts();
        return new SearchDto.Stats(counts, counts.values().stream().mapToLong(Long::longValue).sum());
    }

    /** 인덱스 전체 재생성 (관리자) — 몇천 건 기준 수 초 */
    @PostMapping("/reindex")
    public SearchDto.Stats reindex() {
        CurrentUser.requireAdmin();
        Map<String, Long> counts = indexService.reindexAll();
        return new SearchDto.Stats(counts, counts.values().stream().mapToLong(Long::longValue).sum());
    }
}
