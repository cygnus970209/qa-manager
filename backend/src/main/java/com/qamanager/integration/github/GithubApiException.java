package com.qamanager.integration.github;

import com.qamanager.common.ApiException;
import org.springframework.http.HttpStatus;

/**
 * GitHub API 호출 실패. ApiException 을 상속해 컨트롤러 경로에서는 502 로 내려가고,
 * 비동기 발송 경로에서는 호출부에서 잡아 로그만 남긴다.
 */
public class GithubApiException extends ApiException {

    public GithubApiException(String message) {
        super(HttpStatus.BAD_GATEWAY, "GITHUB_API_ERROR", message);
    }

    public GithubApiException(String message, Throwable cause) {
        super(HttpStatus.BAD_GATEWAY, "GITHUB_API_ERROR", message);
        initCause(cause);
    }
}
