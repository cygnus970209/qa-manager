package com.qamanager.integration.github;

import com.qamanager.qa.item.QaItemService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * QA 도메인 이벤트를 구독해 GitHub 동기화를 트리거한다.
 * 발송은 트랜잭션 커밋 후 (rollback 시 이슈 생성/변경 방지) + 비동기 ({@code NotificationService} 패턴).
 */
@Component
public class GithubIssueSyncListener {

    private final GithubIssueService issueService;
    private final QaGithubIssueRepository issueRepository;

    public GithubIssueSyncListener(GithubIssueService issueService, QaGithubIssueRepository issueRepository) {
        this.issueService = issueService;
        this.issueRepository = issueRepository;
    }

    @EventListener
    public void onQaCreated(QaItemService.QaCreatedEvent ev) {
        if (!ev.createGithubIssue()) return;
        afterCommit(() -> issueService.createIssueForQa(ev.qaItemId()));
    }

    @EventListener
    public void onQaStatusChanged(QaItemService.QaStatusChangedEvent ev) {
        // 이슈가 연결된 QA 만 비동기 동기화 대상 (대부분의 QA 는 여기서 걸러짐).
        if (!issueRepository.existsByQaItemId(ev.qaItemId())) return;
        afterCommit(() -> issueService.syncIssueState(ev.qaItemId(), ev.newStatus()));
    }

    private void afterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }
}
