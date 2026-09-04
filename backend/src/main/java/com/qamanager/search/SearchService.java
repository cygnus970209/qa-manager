package com.qamanager.search;

import com.qamanager.project.Project;
import com.qamanager.project.ProjectRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 통합 검색 질의.
 * - MATCH ... AGAINST (BOOLEAN MODE, 바이그램 토큰) 로 후보를 고르고, 제목 일치 → 관련도 → 최신순으로 정렬
 * - 질의가 "#123" / "123" 이면 그 번호의 QA 를 맨 앞에 붙인다
 * - 한 글자 질의처럼 토큰을 못 만들면 search_text LIKE 로 폴백
 */
@Service
public class SearchService {

    public static final Set<String> TYPES = Set.of(
        SearchDocument.TYPE_QA, SearchDocument.TYPE_COMMENT, SearchDocument.TYPE_PROJECT,
        SearchDocument.TYPE_UPDATE, SearchDocument.TYPE_TEST_CASE);
    private static final int SNIPPET_RADIUS = 60;
    private static final DateTimeFormatter TS = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final EntityManager em;
    private final SearchDocumentRepository documents;
    private final ProjectRepository projects;

    public SearchService(EntityManager em, SearchDocumentRepository documents, ProjectRepository projects) {
        this.em = em;
        this.documents = documents;
        this.projects = projects;
    }

    @Transactional(readOnly = true)
    public SearchDto.Response search(String rawQuery, List<String> types, Long projectId, int page, int size) {
        String query = rawQuery == null ? "" : rawQuery.trim();
        List<String> typeFilter = types == null ? List.of() : types.stream().filter(TYPES::contains).toList();
        if (query.length() < 1) {
            return new SearchDto.Response(query, 0, Map.of(), List.of(), page, size);
        }

        String booleanQuery = SearchTokenizer.booleanQuery(query);
        String like = "%" + SearchTokenizer.normalize(query) + "%";
        // 조건: 전문검색 또는 (토큰이 없을 때) LIKE
        String match = booleanQuery != null
            ? "MATCH(d.ngram_text) AGAINST(:q IN BOOLEAN MODE)"
            : "d.search_text LIKE :like";

        StringBuilder where = new StringBuilder(match);
        if (projectId != null) where.append(" AND d.project_id = :pid");
        String whereWithType = where + (typeFilter.isEmpty() ? "" : " AND d.entity_type IN (:types)");

        // 종류별 건수 (종류 필터 제외)
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String t : List.of(SearchDocument.TYPE_QA, SearchDocument.TYPE_COMMENT, SearchDocument.TYPE_PROJECT,
            SearchDocument.TYPE_UPDATE, SearchDocument.TYPE_TEST_CASE)) counts.put(t, 0L);
        Query countQ = em.createNativeQuery("SELECT d.entity_type, COUNT(*) FROM search_document d WHERE " + where + " GROUP BY d.entity_type");
        bind(countQ, booleanQuery, like, projectId, null);
        for (Object row : countQ.getResultList()) {
            Object[] r = (Object[]) row;
            counts.put((String) r[0], ((Number) r[1]).longValue());
        }

        Query totalQ = em.createNativeQuery("SELECT COUNT(*) FROM search_document d WHERE " + whereWithType);
        bind(totalQ, booleanQuery, like, projectId, typeFilter);
        long total = ((Number) totalQ.getSingleResult()).longValue();

        String order = booleanQuery != null
            ? " ORDER BY (LOWER(d.title) LIKE :like) DESC, MATCH(d.ngram_text) AGAINST(:q IN BOOLEAN MODE) DESC, d.updated_at DESC"
            : " ORDER BY (LOWER(d.title) LIKE :like) DESC, d.updated_at DESC";
        Query listQ = em.createNativeQuery("SELECT d.* FROM search_document d WHERE " + whereWithType + order, SearchDocument.class);
        bind(listQ, booleanQuery, like, projectId, typeFilter);
        listQ.setParameter("like", like); // ORDER BY 에서도 쓴다
        listQ.setFirstResult(page * size);
        listQ.setMaxResults(size);
        @SuppressWarnings("unchecked")
        List<SearchDocument> docs = new ArrayList<>(listQ.getResultList());

        // "#123" / "123" → 그 번호의 QA 를 첫 페이지 맨 앞에
        if (page == 0 && (typeFilter.isEmpty() || typeFilter.contains(SearchDocument.TYPE_QA))) {
            String digits = query.startsWith("#") ? query.substring(1) : query;
            if (digits.matches("\\d{1,9}")) {
                documents.findByEntityTypeAndEntityId(SearchDocument.TYPE_QA, Long.parseLong(digits)).ifPresent(d -> {
                    if (projectId == null || projectId.equals(d.getProjectId())) {
                        docs.removeIf(x -> x.getId().equals(d.getId()));
                        docs.add(0, d);
                    }
                });
            }
        }

        // 프로젝트 이름은 질의 시점에 붙인다 (이름이 바뀌어도 인덱스가 오래되지 않게)
        Map<Long, String> projectNames = new HashMap<>();
        List<Long> pids = docs.stream().map(SearchDocument::getProjectId).filter(x -> x != null).distinct().toList();
        if (!pids.isEmpty()) {
            for (Project p : projects.findAllById(pids)) projectNames.put(p.getId(), p.getName());
        }
        List<String> words = SearchTokenizer.words(query);
        List<SearchDto.Item> items = docs.stream().map(d -> new SearchDto.Item(
            d.getEntityType(), d.getEntityId(), d.getTitle(), snippet(d.getBody(), words),
            d.getProjectId(), projectNames.get(d.getProjectId()), d.getUpdateId(), d.getQaItemId(),
            d.getStatus(), d.getUpdatedAt() == null ? null : TS.format(d.getUpdatedAt()))).toList();

        return new SearchDto.Response(query, total, counts, items, page, size);
    }

    private static void bind(Query q, String booleanQuery, String like, Long projectId, List<String> types) {
        if (booleanQuery != null) q.setParameter("q", booleanQuery);
        else q.setParameter("like", like);
        if (projectId != null) q.setParameter("pid", projectId);
        if (types != null && !types.isEmpty()) q.setParameter("types", types);
    }

    /** 본문에서 첫 질의어 주변을 잘라 낸다. 없으면 앞부분 */
    static String snippet(String body, List<String> words) {
        if (body == null || body.isBlank()) return "";
        String flat = body.replaceAll("\\s+", " ").trim();
        String lower = flat.toLowerCase(Locale.ROOT);
        int at = -1;
        for (String w : words) {
            at = lower.indexOf(w);
            if (at >= 0) break;
        }
        if (at < 0) return flat.length() <= SNIPPET_RADIUS * 2 ? flat : flat.substring(0, SNIPPET_RADIUS * 2) + "…";
        int start = Math.max(0, at - SNIPPET_RADIUS);
        int end = Math.min(flat.length(), at + SNIPPET_RADIUS * 2);
        return (start > 0 ? "…" : "") + flat.substring(start, end) + (end < flat.length() ? "…" : "");
    }
}
