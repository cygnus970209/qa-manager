package com.qamanager.integration.github;

import com.qamanager.project.Project;
import com.qamanager.qa.item.QaItem;
import com.qamanager.qa.item.QaItemRepository;
import com.qamanager.qa.shared.QaStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * QA ↔ GitHub 이슈 동기화 (단방향: QA → GitHub).
 *
 * 정책:
 * - 메인 트랜잭션과 분리 (@Async + 새 트랜잭션, 커밋 후 호출). GitHub 실패가 QA 저장에 영향 없도록.
 * - 미설정/미연결이면 조용히 skip (Teams 발송과 동일한 no-op 게이트).
 * - 상태 매핑: fix_done/confirmed → close, needs_fix/in_progress/needs_recheck → reopen, on_hold → 유지.
 */
@Service
public class GithubIssueService {

    private static final Logger log = LoggerFactory.getLogger(GithubIssueService.class);

    private final GithubAppRepository appRepository;
    private final QaGithubIssueRepository issueRepository;
    private final QaItemRepository qaRepository;
    private final GithubClient client;
    private final String linkBaseUrl;

    public GithubIssueService(GithubAppRepository appRepository,
                              QaGithubIssueRepository issueRepository,
                              QaItemRepository qaRepository,
                              GithubClient client,
                              @Value("${app.cors.allowed-origins:}") String allowedOrigins) {
        this.appRepository = appRepository;
        this.issueRepository = issueRepository;
        this.qaRepository = qaRepository;
        this.client = client;
        this.linkBaseUrl = firstOrigin(allowedOrigins);
    }

    /* ─────────────── QA 생성 → 이슈 생성 ─────────────── */

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createIssueForQa(Long qaItemId) {
        GithubApp app = appRepository.findTopByOrderByIdAsc().orElse(null);
        if (app == null) {
            log.debug("GitHub App 미설정 — 이슈 생성 skip (qa={})", qaItemId);
            return;
        }
        QaItem qa = qaRepository.findById(qaItemId).orElse(null);
        if (qa == null) return;
        Project project = qa.getProjectUpdate().getProject();
        if (project.getGithubInstallationId() == null
            || project.getGithubRepoOwner() == null || project.getGithubRepoName() == null) {
            log.debug("프로젝트에 repo 미연결 — 이슈 생성 skip (qa={}, project={})", qaItemId, project.getId());
            return;
        }
        if (issueRepository.existsByQaItemId(qaItemId)) return; // 중복 생성 방지

        try {
            GithubDto.IssueRef ref = client.createIssue(app, project.getGithubInstallationId(),
                project.getGithubRepoOwner(), project.getGithubRepoName(),
                qa.getTitle(), buildIssueBody(qa));
            issueRepository.save(new QaGithubIssue(qaItemId,
                project.getGithubRepoOwner(), project.getGithubRepoName(),
                ref.number(), ref.htmlUrl(), ref.state()));
            log.info("GitHub 이슈 생성: {}/{}#{} (qa={})",
                project.getGithubRepoOwner(), project.getGithubRepoName(), ref.number(), qaItemId);
        } catch (Exception e) {
            log.warn("GitHub 이슈 생성 실패 (qa={}): {}", qaItemId, e.getMessage());
        }
    }

    /* ─────────────── QA 상태 변경 → close / reopen ─────────────── */

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void syncIssueState(Long qaItemId, String newStatusCode) {
        QaGithubIssue mapping = issueRepository.findByQaItemId(qaItemId).orElse(null);
        if (mapping == null) return;
        String targetState = targetIssueState(newStatusCode);
        if (targetState == null || targetState.equals(mapping.getState())) return;

        GithubApp app = appRepository.findTopByOrderByIdAsc().orElse(null);
        if (app == null) return;

        try {
            Long installationId = client
                .findRepoInstallation(app, mapping.getRepoOwner(), mapping.getRepoName())
                .orElse(null);
            if (installationId == null) {
                log.warn("repo 에 앱 미설치 — 이슈 상태 동기화 skip ({}/{}#{})",
                    mapping.getRepoOwner(), mapping.getRepoName(), mapping.getIssueNumber());
                return;
            }
            client.setIssueState(app, installationId, mapping.getRepoOwner(), mapping.getRepoName(),
                mapping.getIssueNumber(), targetState);
            mapping.updateState(targetState);
            log.info("GitHub 이슈 상태 동기화: {}/{}#{} → {} (qa={}, status={})",
                mapping.getRepoOwner(), mapping.getRepoName(), mapping.getIssueNumber(),
                targetState, qaItemId, newStatusCode);
        } catch (Exception e) {
            log.warn("GitHub 이슈 상태 동기화 실패 (qa={}, status={}): {}", qaItemId, newStatusCode, e.getMessage());
        }
    }

    /** QA 상태 → 이슈 목표 상태. null 이면 변경하지 않음 (on_hold 등). */
    static String targetIssueState(String statusCode) {
        if (QaStatus.FIX_DONE.getCode().equals(statusCode) || QaStatus.CONFIRMED.getCode().equals(statusCode)) {
            return QaGithubIssue.STATE_CLOSED;
        }
        if (QaStatus.NEEDS_FIX.getCode().equals(statusCode)
            || QaStatus.IN_PROGRESS.getCode().equals(statusCode)
            || QaStatus.NEEDS_RECHECK.getCode().equals(statusCode)) {
            return QaGithubIssue.STATE_OPEN;
        }
        return null;
    }

    /* ─────────────── 참조 커밋 조회 ─────────────── */

    // 주의: GitHub API 다회 호출 구간이라 트랜잭션(DB 커넥션 점유) 없이 동작한다.
    public List<GithubDto.Commit> listCommits(Long qaItemId) {
        QaGithubIssue mapping = issueRepository.findByQaItemId(qaItemId).orElse(null);
        if (mapping == null) return List.of();
        GithubApp app = appRepository.findTopByOrderByIdAsc().orElse(null);
        if (app == null) return List.of();

        Long installationId = client
            .findRepoInstallation(app, mapping.getRepoOwner(), mapping.getRepoName())
            .orElse(null);
        if (installationId == null) return List.of();
        return client.listIssueCommits(app, installationId,
            mapping.getRepoOwner(), mapping.getRepoName(), mapping.getIssueNumber());
    }

    /* ─────────────── helpers ─────────────── */

    private String buildIssueBody(QaItem qa) {
        StringBuilder sb = new StringBuilder();
        if (qa.getDescription() != null && !qa.getDescription().isBlank()) {
            sb.append(qa.getDescription()).append("\n\n");
        }
        sb.append("---\n");
        if (!linkBaseUrl.isBlank()) {
            sb.append("> QA Manager: ").append(linkBaseUrl).append("/qa/").append(qa.getId()).append("\n");
        }
        sb.append("> 커밋 메시지에 이 이슈 번호를 `#번호` 형태로 포함하면 QA 상세에서 해당 커밋이 추적됩니다.");
        return sb.toString();
    }

    /** CORS allowed-origins 의 첫 origin 을 QA 링크 base 로 사용 ({@code TeamsNotifier} 와 동일). */
    private static String firstOrigin(String allowedOrigins) {
        if (allowedOrigins == null || allowedOrigins.isBlank()) return "";
        String first = allowedOrigins.split(",")[0].trim();
        return first.endsWith("/") ? first.substring(0, first.length() - 1) : first;
    }
}
