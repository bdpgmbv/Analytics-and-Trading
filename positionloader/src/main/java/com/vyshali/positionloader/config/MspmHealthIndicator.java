package com.vyshali.positionloader.config;

/*
 * SIMPLIFIED: Health Indicators
 *
 * BEFORE: 4 separate custom health indicator classes in one file (~120 lines)
 *   - MspmHealthIndicator
 *   - KafkaHealthIndicator
 *   - RedisHealthIndicator
 *   - DatabaseHealthIndicator
 *
 * AFTER: Use Spring Boot auto-configuration + 1 custom indicator for MSPM
 *
 * WHY:
 * - Spring Boot auto-configures health checks for Kafka, Redis, DB
 * - Only MSPM needs custom check (external service)
 * - Reduces 120 lines to ~40 lines
 *
 * SPRING BOOT AUTO-CONFIGURED HEALTH INDICATORS:
 * ┌─────────────────────────────────────────────────────────────────┐
 * │ Dependency              │ Auto Health Indicator                │
 * ├─────────────────────────────────────────────────────────────────┤
 * │ spring-boot-starter-    │ DataSourceHealthIndicator            │
 * │ data-jpa                │ (checks DB connection)               │
 * ├─────────────────────────────────────────────────────────────────┤
 * │ spring-kafka            │ KafkaHealthIndicator                 │
 * │                         │ (checks broker connectivity)         │
 * ├─────────────────────────────────────────────────────────────────┤
 * │ spring-boot-starter-    │ RedisHealthIndicator                 │
 * │ data-redis              │ (checks Redis PING)                  │
 * └─────────────────────────────────────────────────────────────────┘
 *
 * These are AUTOMATICALLY enabled - no code needed!
 */

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

/**
 * Custom health indicator for MSPM external service.
 *
 * This is the ONLY custom health indicator needed.
 * Kafka, Redis, and Database health checks are auto-configured by Spring Boot.
 */
@Component("mspm")
public class MspmHealthIndicator implements HealthIndicator {

    private final WebClient mspmWebClient;
    private final Duration timeout;

    public MspmHealthIndicator(
            WebClient mspmWebClient,
            @Value("${mspm.health-check-timeout:5s}") Duration timeout) {
        this.mspmWebClient = mspmWebClient;
        this.timeout = timeout;
    }

    @Override
    public Health health() {
        try {
            // Simple ping to MSPM health endpoint
            String response = mspmWebClient.get()
                    .uri("/actuator/health")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(timeout)
                    .block();

            return Health.up()
                    .withDetail("service", "MSPM")
                    .withDetail("response", response != null ? "OK" : "Empty")
                    .build();

        } catch (Exception e) {
            return Health.down()
                    .withDetail("service", "MSPM")
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}