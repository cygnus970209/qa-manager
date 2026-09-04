package com.qamanager.search;

import com.qamanager.project.Project;
import com.qamanager.project.ProjectRepository;
import com.qamanager.projectupdate.ProjectUpdate;
import com.qamanager.projectupdate.ProjectUpdateRepository;
import com.qamanager.qa.comment.QaComment;
import com.qamanager.qa.comment.QaCommentRepository;
import com.qamanager.qa.item.QaItem;
import com.qamanager.qa.item.QaItemRepository;
import com.qamanager.testing.TestCase;
import com.qamanager.testing.TestCaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 검색 인덱스 쓰기. 엔티티 하나 → search_document 한 행.
 * - 개별 갱신: SearchIndexListener 가 커밋 후 호출 (REQUIRES_NEW — 원래 트랜잭션은 이미 끝났다)
 * - 전체 재생성: 앱 시작 시 테이블이 비어 있을 때(SearchIndexBootstrap) 와 관리자 화면 버튼
 */
@Service
public class SearchIndexService {

    private static final Logger log = LoggerFactory.getLogger(SearchIndexService.class);
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final SearchDocumentRepository documents;
    private final QaItemRepository qaItems;
    private final QaCommentRepository comments;
    private final ProjectRepository projects;
    private final ProjectUpdateRepository updates;
    private final TestCaseRepository testCases;

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
            // 검색 인덱스 갱신 실패가 원래 작업에 영향을 주면 안 된다 — 로그만 남기고 다음 전체 재생성에서 복구
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

    @Transactional
    public Map<String, Long> reindexAll() {
        long started = System.currentTimeMillis();
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
        qs.forEach(this::indexQa);
        counts.put(SearchDocument.TYPE_QA, (long) qs.size());
        List<QaComment> cs = comments.findAll();
        cs.forEach(this::indexComment);
        counts.put(SearchDocument.TYPE_COMMENT, (long) cs.size());
        List<TestCase> ts = testCases.findAll();
        ts.forEach(this::indexTestCase);
        counts.put(SearchDocument.TYPE_TEST_CASE, (long) ts.size());
        log.info("검색 인덱스 재생성 완료 {} ({}ms)", counts, System.currentTimeMillis() - started);
        return counts;
    }

    @Transactional(readOnly = true)
    public Map<String, Long> counts() {
        Map<String, Long> counts = new HashMap<>();
        for (Object[] row : documents.countByType()) {
            counts.put((String) row[0], (Long) row[1]);
        }
        return counts;
    }

    @Transactional(readOnly = true)
    public boolean isEmpty() {
        return documents.count() == 0;
    }

    /* ─────────────── 문서 만들기 ─────────────── */

    private SearchDocument doc(String type, Long id) {
        return documents.findByEntityTypeAndEntityId(type, id).orElseGet(() -> new SearchDocument(type, id));
    }

    private void indexProject(Project p) {
        SearchDocument d = doc(SearchDocument.TYPE_PROJECT, p.getId());
        d.fill(p.getId(), null, null, p.getName(), p.getDescription(), p.getStatus().getCode());
        documents.save(d);
    }

    private void indexUpdate(ProjectUpdate u) {
        SearchDocument d = doc(SearchDocument.TYPE_UPDATE, u.getId());
        d.fill(u.getProject().getId(), u.getId(), null, u.getVersion() + " " + u.getTitle(), u.getDescription(), u.getStatus().getCode());
        documents.save(d);
    }

    private void indexQa(QaItem q) {
        ProjectUpdate u = q.getProjectUpdate();
        SearchDocument d = doc(SearchDocument.TYPE_QA, q.getId());
        String body = q.getCategory() == null || q.getCategory().isBlank()
            ? q.getDescription()
            : "[" + q.getCategory() + "] " + (q.getDescription() == null ? "" : q.getDescription());
        d.fill(u.getProject().getId(), u.getId(), q.getId(), q.getTitle(), body, q.getStatus().getCode());
        documents.save(d);
        // 코멘트 문서는 QA 제목을 함께 담으므로 QA 제목이 바뀌면 같이 갱신
        for (SearchDocument c : documents.findAllByEntityTypeAndQaItemId(SearchDocument.TYPE_COMMENT, q.getId())) {
            comments.findById(c.getEntityId()).ifPresent(this::indexComment);
        }
    }

    private void indexComment(QaComment c) {
        QaItem q = c.getQaItem();
        ProjectUpdate u = q.getProjectUpdate();
        SearchDocument d = doc(SearchDocument.TYPE_COMMENT, c.getId());
        d.fill(u.getProject().getId(), u.getId(), q.getId(), "#" + q.getId() + " " + q.getTitle(), c.getContent(), q.getStatus().getCode());
        documents.save(d);
    }

    private void indexTestCase(TestCase t) {
        SearchDocument d = doc(SearchDocument.TYPE_TEST_CASE, t.getId());
        StringBuilder body = new StringBuilder();
        if (t.getPrecondition() != null && !t.getPrecondition().isBlank()) body.append(t.getPrecondition()).append('\n');
        body.append(stepsText(t.getStepsJson()));
        d.fill(t.getProjectId(), null, null, t.getTitle(), body.toString(), t.getPriority());
        documents.save(d);
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
