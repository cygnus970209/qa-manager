package com.qamanager.search;

import com.qamanager.auth.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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

    /* ─────────────── 관리자: 인덱스 현황 · 검사 · 복구 · 재생성 ─────────────── */

    /** 종류별 원본/색인 건수, 마지막 재생성 정보 */
    @GetMapping("/status")
    public SearchDto.Status status() {
        CurrentUser.requireAdmin();
        return indexService.status();
    }

    /** 원본과 문서를 대조해 누락·고아·내용 변경을 찾는다 (읽기 전용) */
    @PostMapping("/check")
    public SearchDto.Check check() {
        CurrentUser.requireAdmin();
        return indexService.check();
    }

    /** 검사에서 나온 불일치만 고친다 → 고친 뒤 다시 검사한 결과 */
    @PostMapping("/repair")
    public SearchDto.Check repair() {
        CurrentUser.requireAdmin();
        return indexService.repair();
    }

    /** 인덱스 전체 재생성 — 몇천 건 기준 수 초 */
    @PostMapping("/reindex")
    public SearchDto.Status reindex() {
        CurrentUser.requireAdmin();
        indexService.reindexAll("manual");
        return indexService.status();
    }
}
