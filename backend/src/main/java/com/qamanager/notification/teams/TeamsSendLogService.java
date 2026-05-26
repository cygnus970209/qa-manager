package com.qamanager.notification.teams;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Teams 발송 로그를 비동기로 적재한다. 적재 자체 실패는 swallow + warn.
 */
@Service
public class TeamsSendLogService {

    private static final Logger log = LoggerFactory.getLogger(TeamsSendLogService.class);

    private final TeamsSendLogRepository repository;

    public TeamsSendLogService(TeamsSendLogRepository repository) {
        this.repository = repository;
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String status, String notificationType, Long recipientId,
                       String recipientEmail, String message, String detail, Integer durationMs) {
        try {
            repository.save(new TeamsSendLog(
                status, notificationType, recipientId, recipientEmail, message, detail, durationMs
            ));
        } catch (Exception e) {
            log.warn("teams_send_log 적재 실패 (status={}, recipient={}): {}",
                status, recipientId, e.getMessage());
        }
    }
}
