package com.vyshali.positionloader.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import java.util.List;
import java.util.Collections;

/**
 * Centralized configuration properties for the Position Loader service.
 * Binds to 'loader.*' properties in application.yml.
 * 
 * Usage in application.yml:
 * <pre>
 * loader:
 *   batch-size: 1000
 *   processing-threads: 4
 *   dlq-retention-days: 7
 *   dlq-max-retries: 3
 *   dlq-retry-interval-ms: 300000
 *   rate-limit:
 *     max-concurrent: 20
 *     requests-per-second: 100
 *   alerting:
 *     dlq-threshold: 100
 *     email-enabled: true
 *   consumer-lag:
 *     check-interval-ms: 30000
 *     alert-threshold: 10000
 *   mspm:
 *     base-url: https://mspm.internal
 *     timeout: 30s
 *   retry:
 *     max-attempts: 3
 *     initial-delay: 1s
 *     max-delay: 30s
 *   feature-flags:
 *     eod-processing-enabled: true
 *     intraday-processing-enabled: true
 *     validation-enabled: true
 *     duplicate-detection-enabled: true
 *     reconciliation-enabled: true
 *     archival-enabled: true
 *   validation:
 *     enabled: true
 *     reject-zero-quantity: true
 *     max-price-threshold: 1000000
 *   archival:
 *     enabled: true
 *     retention-days: 180
 *     cron-schedule: "0 0 2 * * SUN"
 * </pre>
 */
@Validated
@ConfigurationProperties(prefix = "loader")
public record LoaderProperties(
    @Positive int batchSize,
    @Positive int processingThreads,
    int dlqRetentionDays,
    int dlqMaxRetries,
    long dlqRetryIntervalMs,
    RateLimitConfig rateLimit,
    AlertingConfig alerting,
    ConsumerLagConfig consumerLag,
    MspmConfig mspm,
    RetryConfig retry,
    FeatureFlagsConfig featureFlags,
    ValidationConfig validation,
    ArchivalConfig archival
) {
    
    /**
     * Default constructor with sensible defaults
     */
    public LoaderProperties {
        if (batchSize <= 0) batchSize = 1000;
        if (processingThreads <= 0) processingThreads = 4;
        if (dlqRetentionDays <= 0) dlqRetentionDays = 7;
        if (dlqMaxRetries <= 0) dlqMaxRetries = 3;
        if (dlqRetryIntervalMs <= 0) dlqRetryIntervalMs = 300000L;
        if (rateLimit == null) rateLimit = new RateLimitConfig(20, 100);
        if (alerting == null) alerting = new AlertingConfig(100, false, null);
        if (consumerLag == null) consumerLag = new ConsumerLagConfig(30000L, 10000L);
        if (mspm == null) mspm = new MspmConfig("http://localhost:8080", Duration.ofSeconds(30));
        if (retry == null) retry = new RetryConfig(3, Duration.ofSeconds(1), Duration.ofSeconds(30), 2.0);
        if (featureFlags == null) featureFlags = new FeatureFlagsConfig(
            true, true, true, true, true, true, Collections.emptyList(), Collections.emptyList());
        if (validation == null) validation = new ValidationConfig(true, true, 1000000);
        if (archival == null) archival = new ArchivalConfig(true, 180, "0 0 2 * * SUN");
    }
    
    /**
     * Rate limiting configuration for external API calls
     */
    public record RateLimitConfig(
        @Positive int maxConcurrent,
        @Positive int requestsPerSecond
    ) {
        public RateLimitConfig {
            if (maxConcurrent <= 0) maxConcurrent = 20;
            if (requestsPerSecond <= 0) requestsPerSecond = 100;
        }
    }
    
    /**
     * Alerting configuration for operational monitoring
     */
    public record AlertingConfig(
        @Positive int dlqThreshold,
        boolean emailEnabled,
        String emailRecipients
    ) {
        public AlertingConfig {
            if (dlqThreshold <= 0) dlqThreshold = 100;
        }
    }
    
    /**
     * Consumer lag monitoring configuration
     */
    public record ConsumerLagConfig(
        @Positive long checkIntervalMs,
        @Positive long alertThreshold
    ) {
        public ConsumerLagConfig {
            if (checkIntervalMs <= 0) checkIntervalMs = 30000L;
            if (alertThreshold <= 0) alertThreshold = 10000L;
        }
        
        public Duration checkInterval() {
            return Duration.ofMillis(checkIntervalMs);
        }
    }
    
    /**
     * MSPM client configuration
     */
    public record MspmConfig(
        @NotBlank String baseUrl,
        Duration timeout
    ) {
        public MspmConfig {
            if (baseUrl == null || baseUrl.isBlank()) baseUrl = "http://localhost:8080";
            if (timeout == null) timeout = Duration.ofSeconds(30);
        }
    }
    
    /**
     * Retry configuration with exponential backoff
     */
    public record RetryConfig(
        @Positive int maxAttempts,
        Duration initialDelay,
        Duration maxDelay,
        double multiplier
    ) {
        public RetryConfig {
            if (maxAttempts <= 0) maxAttempts = 3;
            if (initialDelay == null) initialDelay = Duration.ofSeconds(1);
            if (maxDelay == null) maxDelay = Duration.ofSeconds(30);
            if (multiplier <= 0) multiplier = 2.0;
        }
    }
    
    /**
     * Feature flags for controlling functionality
     */
    public record FeatureFlagsConfig(
        boolean eodProcessingEnabled,
        boolean intradayProcessingEnabled,
        boolean validationEnabled,
        boolean duplicateDetectionEnabled,
        boolean reconciliationEnabled,
        boolean archivalEnabled,
        List<Integer> disabledAccounts,
        List<Integer> pilotAccounts
    ) {
        public FeatureFlagsConfig {
            if (disabledAccounts == null) disabledAccounts = Collections.emptyList();
            if (pilotAccounts == null) pilotAccounts = Collections.emptyList();
        }
    }
    
    /**
     * Validation configuration
     */
    public record ValidationConfig(
        boolean enabled,
        boolean rejectZeroQuantity,
        int maxPriceThreshold
    ) {
        public ValidationConfig {
            if (maxPriceThreshold <= 0) maxPriceThreshold = 1000000;
        }
    }
    
    /**
     * Archival configuration
     */
    public record ArchivalConfig(
        boolean enabled,
        int retentionDays,
        String cronSchedule
    ) {
        public ArchivalConfig {
            if (retentionDays <= 0) retentionDays = 180;
            if (cronSchedule == null || cronSchedule.isBlank()) cronSchedule = "0 0 2 * * SUN";
        }
    }
}
