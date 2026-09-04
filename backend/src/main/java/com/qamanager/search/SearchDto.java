package com.qamanager.search;

import java.util.List;
import java.util.Map;

public class SearchDto {

    /** GET /api/search 응답 */
    public record Response(
        String query,
        long total,
        /** 종류별 건수 — 종류 필터와 무관하게 전체 기준 (탭 숫자용) */
        Map<String, Long> counts,
        List<Item> items,
        int page,
        int size
    ) {}

    public record Item(
        /** qa | comment | project | update | test_case */
        String type,
        Long id,
        String title,
        /** 본문 발췌 (질의어 주변) */
        String snippet,
        Long projectId,
        String projectName,
        Long updateId,
        Long qaItemId,
        String status,
        String updatedAt
    ) {}

    /** 인덱스 현황 (관리자) */
    public record Stats(Map<String, Long> counts, long total) {}
}
