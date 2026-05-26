package com.qamanager.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * api_request_log 를 비동기로 적재한다. 적재 실패는 swallow + warn.
 */
@Service
public class ApiRequestLogService {

    private static final Logger log = LoggerFactory.getLogger(ApiRequestLogService.class);

    private final ApiRequestLogRepository repository;

    public ApiRequestLogService(ApiRequestLogRepository repository) {
        this.repository = repository;
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String method, String path, String queryString, Integer status,
                       Long memberId, String username, String ip, String userAgent,
                       Integer durationMs, String errorMessage) {
        try {
            repository.save(new ApiRequestLog(
                method, path, queryString, status, memberId, username, ip, userAgent, durationMs, errorMessage
            ));
        } catch (Exception e) {
            log.warn("api_request_log 적재 실패 ({} {}): {}", method, path, e.getMessage());
        }
    }
}
