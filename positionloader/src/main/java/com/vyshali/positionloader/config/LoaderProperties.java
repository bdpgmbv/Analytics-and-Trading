package com.vyshali.positionloader.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.time.Duration;

/**
 * Centralized configuration properties for the Position Loader service.
 * Binds to 'loader.*' properties in application.yml.
 * 
 * Usage in application.yml:
 * <pre>
 * loader:
 *   batch-size: 1000
 *   processing-threads: 4
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
 *     duplicate-detection-enabled: true
 *     late-eod-enabled: true
 *     archival-enabled: true
 * </pre>
 */
@Validated
@ConfigurationProperties(prefix = "loader")
public record LoaderProperties(
    @Positive int batchSize,
    @Positive int processingThreads,
    RateLimitConfig rateLimit,
    AlertingConfig alerting,
    ConsumerLagConfig consumerLag,
    MspmConfig mspm,
    RetryConfig retry,
    FeatureFlagsConfig featureFlags
) {
    
    /**
     * Default constructor with sensible defaults
     */
    public LoaderProperties {
        if (batchSize <= 0) batchSize = 1000;
        if (processingThreads <= 0) processingThreads = 4;
        if (rateLimit == null) rateLimit = new RateLimitConfig(20, 100);
        if (alerting == null) alerting = new AlertingConfig(100, false, null);
        if (consumerLag == null) consumerLag = new ConsumerLagConfig(30000L, 10000L);
        if (mspm == null) mspm = new MspmConfig("http://localhost:8080", Duration.ofSeconds(30));
        if (retry == null) retry = new RetryConfig(3, Duration.ofSeconds(1), Duration.ofSeconds(30), 2.0);
        if (featureFlags == null) featureFlags = new FeatureFlagsConfig(true, true, true, true);
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
     * Feature flags for Phase 4 enhancements
     */
    public record FeatureFlagsConfig(
        boolean duplicateDetectionEnabled,
        boolean lateEodEnabled,
        boolean archivalEnabled,
        boolean consumerLagMonitoringEnabled
    ) {}
}
