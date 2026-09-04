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

    /** 인덱스 현황 (관리자). 원본 건수와 색인 건수를 나란히 — 검사 전에도 차이는 바로 보인다 */
    public record Status(
        Map<String, Long> indexed,
        Map<String, Long> source,
        long total,
        /** 마지막 전체 재생성 (앱 재시작 후에는 비어 있을 수 있다) */
        String lastReindexAt,
        Long lastReindexMs,
        /** startup | schedule | manual | repair */
        String lastTrigger,
        boolean running
    ) {}

    /** 색인 상태 검사 결과 (관리자) — 종류별로 원본과 문서를 대조 */
    public record Check(
        String checkedAt,
        boolean ok,
        long issues,
        Map<String, TypeReport> byType
    ) {}

    public record TypeReport(
        long source,
        long indexed,
        /** 원본에는 있는데 문서가 없음 */
        long missing,
        /** 문서는 있는데 원본이 없음 */
        long orphan,
        /** 내용이 바뀌었는데 문서가 갱신되지 않음 (해시 불일치) */
        long stale,
        List<Long> sampleMissing,
        List<Long> sampleOrphan,
        List<Long> sampleStale
    ) {}
}
