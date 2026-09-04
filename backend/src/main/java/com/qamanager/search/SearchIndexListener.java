package com.qamanager.search;

import com.qamanager.project.Project;
import com.qamanager.projectupdate.ProjectUpdate;
import com.qamanager.qa.comment.QaComment;
import com.qamanager.qa.item.QaItem;
import com.qamanager.testing.TestCase;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 검색 대상 엔티티(@EntityListeners)가 저장·수정·삭제되면 커밋 후 인덱스를 갱신한다.
 * 어느 서비스 경로로 바뀌든(관리자 인라인 변경 포함) 빠짐없이 잡히고, 원래 트랜잭션이 실패하면 아무것도 하지 않는다.
 * Spring Boot 가 Hibernate 에 SpringBeanContainer 를 붙여 주므로 리스너에 빈을 주입할 수 있다.
 */
@Component
public class SearchIndexListener {

    private final SearchIndexService indexService;

    public SearchIndexListener(@Lazy SearchIndexService indexService) {
        this.indexService = indexService;
    }

    @PostPersist
    @PostUpdate
    public void onSave(Object entity) {
        String type = typeOf(entity);
        Long id = idOf(entity);
        if (type == null || id == null) return;
        afterCommit(() -> indexService.reindex(type, id));
    }

    @PostRemove
    public void onRemove(Object entity) {
        String type = typeOf(entity);
        Long id = idOf(entity);
        if (type == null || id == null) return;
        afterCommit(() -> indexService.remove(type, id));
    }

    private static void afterCommit(Runnable work) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    work.run();
                }
            });
        } else {
            work.run();
        }
    }

    static String typeOf(Object entity) {
        if (entity instanceof QaItem) return SearchDocument.TYPE_QA;
        if (entity instanceof QaComment) return SearchDocument.TYPE_COMMENT;
        if (entity instanceof Project) return SearchDocument.TYPE_PROJECT;
        if (entity instanceof ProjectUpdate) return SearchDocument.TYPE_UPDATE;
        if (entity instanceof TestCase) return SearchDocument.TYPE_TEST_CASE;
        return null;
    }

    static Long idOf(Object entity) {
        if (entity instanceof QaItem e) return e.getId();
        if (entity instanceof QaComment e) return e.getId();
        if (entity instanceof Project e) return e.getId();
        if (entity instanceof ProjectUpdate e) return e.getId();
        if (entity instanceof TestCase e) return e.getId();
        return null;
    }
}
