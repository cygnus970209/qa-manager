package com.qamanager.file;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.upload")
public record UploadProperties(
    /** 첨부 파일 최대 크기 (MB) — presign 검증과 Content-Length 서명에 사용. */
    long maxFileSizeMb
) {}
