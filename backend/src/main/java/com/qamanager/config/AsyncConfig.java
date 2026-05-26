package com.qamanager.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * @Async 를 활성화한다. 가상 스레드(spring.threads.virtual.enabled=true)와 함께
 * 사용하면 Spring Boot 가 기본 executor 를 가상 스레드 기반으로 잡아준다.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
