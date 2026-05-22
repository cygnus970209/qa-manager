package com.qamanager.file;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.s3")
public record S3Properties(
    String region,
    String bucket,
    String accessKey,
    String secretKey,
    long presignTtlSeconds
) {}
