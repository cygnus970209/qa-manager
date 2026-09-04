package com.qamanager.search;

import com.qamanager.common.ApiException;
import com.qamanager.project.Project;
import com.qamanager.project.ProjectRepository;
import com.qamanager.projectupdate.ProjectUpdate;
import com.qamanager.projectupdate.ProjectUpdateRepository;
import com.qamanager.qa.comment.QaComment;
import com.qamanager.qa.comment.QaCommentRepository;
import com.qamanager.qa.item.QaItem;
import com.qamanager.qa.item.QaItemRepository;
import com.qamanager.search.SearchDocument.Content;
import com.qamanager.testing.TestCase;
import com.qamanager.testing.TestCaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * 검색 인덱스 쓰기·점검. 엔티티 하나 → search_document 한 행.
 * - 개별 갱신: SearchIndexListener 가 커밋 후 호출 (REQUIRES_NEW — 원래 트랜잭션은 이미 끝났다)
 * - 전체 재생성: 앱 시작 시 테이블이 비어 있을 때 · 매일 04:00 · 관리자 버튼
 * - 상태 검사(check): 종류별로 원본과 문서를 대조해 누락/고아/내용 변경을 찾고, repair 는 그것만 고친다
 */
@Service
public class SearchIndexService {

    public static final List<String> TYPES = List.of(
        SearchDocument.TYPE_PROJECT, SearchDocument.TYPE_UPDATE, SearchDocument.TYPE_QA,
        SearchDocument.TYPE_COMMENT, SearchDocument.TYPE_TEST_CASE);

    private static final Logger log = LoggerFactory.getLogger(SearchIndexService.class);
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final DateTimeFormatter TS = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final int SAMPLE = 10;

    private final SearchDocumentRepository documents;
    private final QaItemRepository qaItems;
    private final QaCommentRepository comments;
    private final ProjectRepository projects;
    private final ProjectUpdateRepository updates;
    private final TestCaseRepository testCases;

    /* 마지막 전체 재생성 정보 — 인스턴스 메모리 (재시작하면 비워진다) */
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile LocalDateTime lastReindexAt;
    private volatile Long lastReindexMs;
    private volatile String lastTrigger;

    public SearchIndexService(SearchDocumentRepository documents, QaItemRepository qaItems, QaCommentRepository comments,
                              ProjectRepository projects, ProjectUpdateRepository updates, TestCaseRepository testCases) {
        this.documents = documents;
        this.qaItems = qaItems;
        this.comments = comments;
        this.projects = projects;
        this.updates = updates;
        this.testCases = testCases;
    }

    /* ─────────────── 개별 갱신 (리스너에서 커밋 후) ─────────────── */

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reindex(String type, Long id) {
        try {
            switch (type) {
                case SearchDocument.TYPE_QA -> qaItems.findById(id).ifPresentOrElse(this::indexQa, () -> documents.deleteByQaItemId(id));
                case SearchDocument.TYPE_COMMENT -> comments.findById(id).ifPresentOrElse(this::indexComment, () -> documents.deleteByEntityTypeAndEntityId(type, id));
                case SearchDocument.TYPE_PROJECT -> projects.findById(id).ifPresentOrElse(this::indexProject, () -> documents.deleteByProjectId(id));
                case SearchDocument.TYPE_UPDATE -> updates.findById(id).ifPresentOrElse(this::indexUpdate, () -> documents.deleteByUpdateId(id));
                case SearchDocument.TYPE_TEST_CASE -> testCases.findById(id).ifPresentOrElse(this::indexTestCase, () -> documents.deleteByEntityTypeAndEntityId(type, id));
                default -> log.warn("알 수 없는 검색 문서 종류: {}", type);
            }
        } catch (RuntimeException e) {
            // 검색 인덱스 갱신 실패가 원래 작업에 영향을 주면 안 된다 — 로그만 남기고 야간 재생성/복구에서 회복
            log.warn("검색 인덱스 갱신 실패 type={} id={}: {}", type, id, e.toString());
        }
    }

    /** 삭제. 프로젝트/업데이트/QA 는 DB 가 하위 행을 cascade 로 지우므로 인덱스도 상위 id 기준으로 함께 지운다 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void remove(String type, Long id) {
        try {
            switch (type) {
                case SearchDocument.TYPE_PROJECT -> documents.deleteByProjectId(id);
                case SearchDocument.TYPE_UPDATE -> documents.deleteByUpdateId(id);
                case SearchDocument.TYPE_QA -> documents.deleteByQaItemId(id);
                default -> documents.deleteByEntityTypeAndEntityId(type, id);
            }
        } catch (RuntimeException e) {
            log.warn("검색 인덱스 삭제 실패 type={} id={}: {}", type, id, e.toString());
        }
    }

    /** QA 하나의 코멘트 문서를 실제 코멘트와 맞춘다 (코멘트 삭제 시 답글 cascade 대응) */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void resyncComments(Long qaItemId) {
        try {
            documents.findAllByEntityTypeAndQaItemId(SearchDocument.TYPE_COMMENT, qaItemId).forEach(documents::delete);
            documents.flush();
            comments.findAllByQaItemIdOrderByCreatedAtAsc(qaItemId).forEach(this::indexComment);
        } catch (RuntimeException e) {
            log.warn("검색 인덱스 코멘트 재동기화 실패 qaItemId={}: {}", qaItemId, e.toString());
        }
    }

    /* ─────────────── 전체 재생성 ─────────────── */

    /** @param trigger startup | schedule | manual — 현황 화면에 보여준다 */
    @Transactional
    public Map<String, Long> reindexAll(String trigger) {
        if (!running.compareAndSet(false, true)) {
            throw ApiException.conflict("검색 인덱스를 이미 다시 만드는 중입니다.");
        }
        long started = System.currentTimeMillis();
        try {
            documents.deleteAllDocuments();
            documents.flush();
            Map<String, Long> counts = new LinkedHashMap<>();
            List<Project> ps = projects.findAll();
            ps.forEach(this::indexProject);
            counts.put(SearchDocument.TYPE_PROJECT, (long) ps.size());
            List<ProjectUpdate> us = updates.findAll();
            us.forEach(this::indexUpdate);
            counts.put(SearchDocument.TYPE_UPDATE, (long) us.size());
            List<QaItem> qs = qaItems.findAll();
            qs.forEach(q -> index(SearchDocument.TYPE_QA, q.getId(), contentOf(q)));
            counts.put(SearchDocument.TYPE_QA, (long) qs.size());
            List<QaComment> cs = comments.findAll();
            cs.forEach(this::indexComment);
            counts.put(SearchDocument.TYPE_COMMENT, (long) cs.size());
            List<TestCase> ts = testCases.findAll();
            ts.forEach(this::indexTestCase);
            counts.put(SearchDocument.TYPE_TEST_CASE, (long) ts.size());
            long ms = System.currentTimeMillis() - started;
            lastReindexAt = LocalDateTime.now();
            lastReindexMs = ms;
            lastTrigger = trigger;
            log.info("검색 인덱스 재생성 완료 [{}] {} ({}ms)", trigger, counts, ms);
            return counts;
        } finally {
            running.set(false);
        }
    }

    /* ─────────────── 현황 · 검사 · 복구 (관리자) ─────────────── */

    @Transactional(readOnly = true)
    public SearchDto.Status status() {
        Map<String, Long> indexed = new LinkedHashMap<>();
        Map<String, Long> source = new LinkedHashMap<>();
        Map<String, Long> raw = new HashMap<>();
        for (Object[] row : documents.countByType()) raw.put((String) row[0], (Long) row[1]);
        for (String t : TYPES) indexed.put(t, raw.getOrDefault(t, 0L));
        source.put(SearchDocument.TYPE_PROJECT, projects.count());
        source.put(SearchDocument.TYPE_UPDATE, updates.count());
        source.put(SearchDocument.TYPE_QA, qaItems.count());
        source.put(SearchDocument.TYPE_COMMENT, comments.count());
        source.put(SearchDocument.TYPE_TEST_CASE, testCases.count());
        long total = indexed.values().stream().mapToLong(Long::longValue).sum();
        return new SearchDto.Status(indexed, source, total,
            lastReindexAt == null ? null : TS.format(lastReindexAt), lastReindexMs, lastTrigger, running.get());
    }

    /**
     * 종류별로 원본 전체와 문서 전체를 대조한다. 몇천 건 기준 수 초.
     * missing = 원본에만, orphan = 문서에만, stale = 둘 다 있으나 내용 해시가 다름(해시 없는 옛 문서 포함)
     */
    @Transactional(readOnly = true)
    public SearchDto.Check check() {
        Map<String, SearchDto.TypeReport> byType = new LinkedHashMap<>();
        long issues = 0;
        for (String type : TYPES) {
            Map<Long, String> expected = expectedHashes(type);
            Map<Long, String> actual = new HashMap<>();
            for (SearchDocument d : documents.findAllByEntityType(type)) actual.put(d.getEntityId(), d.getContentHash());
            List<Long> missing = new ArrayList<>();
            List<Long> stale = new ArrayList<>();
            List<Long> orphan = new ArrayList<>();
            for (Map.Entry<Long, String> e : expected.entrySet()) {
                if (!actual.containsKey(e.getKey())) missing.add(e.getKey());
                else if (!Objects.equals(actual.get(e.getKey()), e.getValue())) stale.add(e.getKey());
            }
            for (Long id : actual.keySet()) if (!expected.containsKey(id)) orphan.add(id);
            issues += missing.size() + orphan.size() + stale.size();
            byType.put(type, new SearchDto.TypeReport(expected.size(), actual.size(),
                missing.size(), orphan.size(), stale.size(),
                sample(missing), sample(orphan), sample(stale)));
        }
        return new SearchDto.Check(TS.format(LocalDateTime.now()), issues == 0, issues, byType);
    }

    /** check 결과의 불일치만 고친다: 누락·변경은 다시 쓰고, 고아는 지운다. 전체 재생성보다 가볍다 */
    @Transactional
    public SearchDto.Check repair() {
        if (!running.compareAndSet(false, true)) {
            throw ApiException.conflict("검색 인덱스를 이미 다시 만드는 중입니다.");
        }
        long started = System.currentTimeMillis();
        try {
            int fixed = 0;
            for (String type : TYPES) {
                Map<Long, String> expected = expectedHashes(type);
                Map<Long, String> actual = new HashMap<>();
                for (SearchDocument d : documents.findAllByEntityType(type)) actual.put(d.getEntityId(), d.getContentHash());
                for (Map.Entry<Long, String> e : expected.entrySet()) {
                    if (!actual.containsKey(e.getKey()) || !Objects.equals(actual.get(e.getKey()), e.getValue())) {
                        reindexOne(type, e.getKey());
                        fixed++;
                    }
                }
                for (Long id : actual.keySet()) {
                    if (!expected.containsKey(id)) {
                        documents.deleteByEntityTypeAndEntityId(type, id);
                        fixed++;
                    }
                }
            }
            long ms = System.currentTimeMillis() - started;
            lastReindexAt = LocalDateTime.now();
            lastReindexMs = ms;
            lastTrigger = "repair";
            log.info("검색 인덱스 복구 완료 — {}건 고침 ({}ms)", fixed, ms);
        } finally {
            running.set(false);
        }
        return check();
    }

    private void reindexOne(String type, Long id) {
        switch (type) {
            case SearchDocument.TYPE_QA -> qaItems.findById(id).ifPresent(q -> index(type, id, contentOf(q)));
            case SearchDocument.TYPE_COMMENT -> comments.findById(id).ifPresent(this::indexComment);
            case SearchDocument.TYPE_PROJECT -> projects.findById(id).ifPresent(this::indexProject);
            case SearchDocument.TYPE_UPDATE -> updates.findById(id).ifPresent(this::indexUpdate);
            case SearchDocument.TYPE_TEST_CASE -> testCases.findById(id).ifPresent(this::indexTestCase);
            default -> { }
        }
    }

    /** 종류별 원본 전체의 (id → 내용 해시) */
    private Map<Long, String> expectedHashes(String type) {
        return switch (type) {
            case SearchDocument.TYPE_PROJECT -> hashes(projects.findAll(), Project::getId, this::contentOf);
            case SearchDocument.TYPE_UPDATE -> hashes(updates.findAll(), ProjectUpdate::getId, this::contentOf);
            case SearchDocument.TYPE_QA -> hashes(qaItems.findAll(), QaItem::getId, this::contentOf);
            case SearchDocument.TYPE_COMMENT -> hashes(comments.findAll(), QaComment::getId, this::contentOf);
            case SearchDocument.TYPE_TEST_CASE -> hashes(testCases.findAll(), TestCase::getId, this::contentOf);
            default -> Map.of();
        };
    }

    private static <T> Map<Long, String> hashes(List<T> list, Function<T, Long> id, Function<T, Content> content) {
        Map<Long, String> out = new HashMap<>();
        for (T t : list) out.put(id.apply(t), content.apply(t).hash());
        return out;
    }

    private static List<Long> sample(List<Long> ids) {
        return ids.size() <= SAMPLE ? ids : ids.subList(0, SAMPLE);
    }

    @Transactional(readOnly = true)
    public boolean isEmpty() {
        return documents.count() == 0;
    }

    /* ─────────────── 문서 내용 ─────────────── */

    private void index(String type, Long id, Content content) {
        SearchDocument d = documents.findByEntityTypeAndEntityId(type, id).orElseGet(() -> new SearchDocument(type, id));
        d.fill(content);
        documents.save(d);
    }

    Content contentOf(Project p) {
        return new Content(p.getId(), null, null, p.getName(), p.getDescription(), p.getStatus().getCode());
    }

    Content contentOf(ProjectUpdate u) {
        return new Content(u.getProject().getId(), u.getId(), null, u.getVersion() + " " + u.getTitle(), u.getDescription(), u.getStatus().getCode());
    }

    Content contentOf(QaItem q) {
        ProjectUpdate u = q.getProjectUpdate();
        String body = q.getCategory() == null || q.getCategory().isBlank()
            ? q.getDescription()
            : "[" + q.getCategory() + "] " + (q.getDescription() == null ? "" : q.getDescription());
        return new Content(u.getProject().getId(), u.getId(), q.getId(), q.getTitle(), body, q.getStatus().getCode());
    }

    Content contentOf(QaComment c) {
        QaItem q = c.getQaItem();
        ProjectUpdate u = q.getProjectUpdate();
        return new Content(u.getProject().getId(), u.getId(), q.getId(), "#" + q.getId() + " " + q.getTitle(), c.getContent(), q.getStatus().getCode());
    }

    Content contentOf(TestCase t) {
        StringBuilder body = new StringBuilder();
        if (t.getPrecondition() != null && !t.getPrecondition().isBlank()) body.append(t.getPrecondition()).append('\n');
        body.append(stepsText(t.getStepsJson()));
        return new Content(t.getProjectId(), null, null, t.getTitle(), body.toString(), t.getPriority());
    }

    private void indexProject(Project p) { index(SearchDocument.TYPE_PROJECT, p.getId(), contentOf(p)); }
    private void indexUpdate(ProjectUpdate u) { index(SearchDocument.TYPE_UPDATE, u.getId(), contentOf(u)); }
    private void indexComment(QaComment c) { index(SearchDocument.TYPE_COMMENT, c.getId(), contentOf(c)); }
    private void indexTestCase(TestCase t) { index(SearchDocument.TYPE_TEST_CASE, t.getId(), contentOf(t)); }

    private void indexQa(QaItem q) {
        index(SearchDocument.TYPE_QA, q.getId(), contentOf(q));
        // 코멘트 문서는 QA 제목·상태를 함께 담으므로 QA 가 바뀌면 같이 갱신
        for (SearchDocument c : documents.findAllByEntityTypeAndQaItemId(SearchDocument.TYPE_COMMENT, q.getId())) {
            comments.findById(c.getEntityId()).ifPresent(this::indexComment);
        }
    }

    /** steps_json [{action, expected}] → "행동 → 기대결과" 줄들. 파싱 실패 시 원문 그대로 */
    static String stepsText(String stepsJson) {
        if (stepsJson == null || stepsJson.isBlank()) return "";
        try {
            JsonNode arr = MAPPER.readTree(stepsJson);
            if (!arr.isArray()) return stepsJson;
            StringBuilder sb = new StringBuilder();
            for (JsonNode step : arr) {
                String action = step.path("action").asString("");
                String expected = step.path("expected").asString("");
                if (action.isBlank() && expected.isBlank()) continue;
                if (sb.length() > 0) sb.append('\n');
                sb.append(action);
                if (!expected.isBlank()) sb.append(" → ").append(expected);
            }
            return sb.toString();
        } catch (RuntimeException e) {
            return stepsJson;
        }
    }
}
