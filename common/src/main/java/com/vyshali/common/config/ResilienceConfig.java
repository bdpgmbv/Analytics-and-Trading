package com.vyshali.common.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Centralized resilience configuration for circuit breakers, retries, and rate limiters.
 * Shared across all FX Analyzer services.
 */
@Slf4j
@Configuration
public class ResilienceConfig {

    // ═══════════════════════════════════════════════════════════════════════════
    // SERVICE NAME CONSTANTS (use these when creating circuit breakers)
    // ═══════════════════════════════════════════════════════════════════════════

    public static final String REDIS = "redis";
    public static final String KAFKA = "kafka";
    public static final String DATABASE = "database";
    public static final String EXTERNAL_API = "external-api";
    public static final String MSPM_SERVICE = "mspm-service";
    public static final String PRICE_SERVICE = "price-service";
    public static final String FX_MATRIX = "fx-matrix";
    public static final String HEDGE_SERVICE = "hedge-service";

    // ═══════════════════════════════════════════════════════════════════════════
    // CIRCUIT BREAKER CONFIGURATION
    // ═══════════════════════════════════════════════════════════════════════════

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        // Default configuration for all circuit breakers
        CircuitBreakerConfig defaultConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)                          // Open after 50% failures
                .slowCallRateThreshold(80)                         // Open after 80% slow calls
                .slowCallDurationThreshold(Duration.ofSeconds(5))  // Call is slow if > 5s
                .waitDurationInOpenState(Duration.ofSeconds(30))   // Wait 30s before half-open
                .permittedNumberOfCallsInHalfOpenState(5)          // Allow 5 calls in half-open
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)                             // Evaluate last 10 calls
                .minimumNumberOfCalls(5)                           // Need 5 calls before evaluating
                .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(defaultConfig);

        // Register service-specific circuit breakers with custom configs

        // Database - more tolerant (slower but critical)
        CircuitBreakerConfig dbConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(70)
                .slowCallDurationThreshold(Duration.ofSeconds(10))
                .waitDurationInOpenState(Duration.ofSeconds(60))
                .slidingWindowSize(20)
                .build();
        registry.circuitBreaker(DATABASE, dbConfig);

        // Redis - strict (fast cache, fail fast)
        CircuitBreakerConfig redisConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(40)
                .slowCallDurationThreshold(Duration.ofMillis(500))
                .waitDurationInOpenState(Duration.ofSeconds(15))
                .slidingWindowSize(10)
                .build();
        registry.circuitBreaker(REDIS, redisConfig);

        // Kafka - moderate
        CircuitBreakerConfig kafkaConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slowCallDurationThreshold(Duration.ofSeconds(3))
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .slidingWindowSize(15)
                .build();
        registry.circuitBreaker(KAFKA, kafkaConfig);

        // MSPM Service - external dependency, more tolerant
        CircuitBreakerConfig mspmConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(60)
                .slowCallDurationThreshold(Duration.ofSeconds(15))
                .waitDurationInOpenState(Duration.ofSeconds(45))
                .slidingWindowSize(10)
                .minimumNumberOfCalls(3)
                .build();
        registry.circuitBreaker(MSPM_SERVICE, mspmConfig);

        // External API - strict timeout, moderate failure threshold
        CircuitBreakerConfig externalConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slowCallDurationThreshold(Duration.ofSeconds(8))
                .waitDurationInOpenState(Duration.ofSeconds(60))
                .slidingWindowSize(10)
                .build();
        registry.circuitBreaker(EXTERNAL_API, externalConfig);

        // Price Service
        CircuitBreakerConfig priceConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slowCallDurationThreshold(Duration.ofSeconds(5))
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .slidingWindowSize(10)
                .build();
        registry.circuitBreaker(PRICE_SERVICE, priceConfig);

        // FX Matrix
        CircuitBreakerConfig fxMatrixConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slowCallDurationThreshold(Duration.ofSeconds(10))
                .waitDurationInOpenState(Duration.ofSeconds(45))
                .slidingWindowSize(10)
                .build();
        registry.circuitBreaker(FX_MATRIX, fxMatrixConfig);

        // Add event consumers for monitoring
        registry.getAllCircuitBreakers().forEach(cb -> {
            cb.getEventPublisher()
                    .onStateTransition(event ->
                            log.warn("Circuit breaker '{}' state transition: {} -> {}",
                                    event.getCircuitBreakerName(),
                                    event.getStateTransition().getFromState(),
                                    event.getStateTransition().getToState()))
                    .onFailureRateExceeded(event ->
                            log.error("Circuit breaker '{}' failure rate exceeded: {}%",
                                    event.getCircuitBreakerName(),
                                    event.getFailureRate()));
        });

        return registry;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // RETRY CONFIGURATION
    // ═══════════════════════════════════════════════════════════════════════════

    @Bean
    public RetryRegistry retryRegistry() {
        RetryConfig defaultConfig = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(500))
                .retryExceptions(Exception.class)
                .ignoreExceptions(IllegalArgumentException.class)
                .build();

        RetryRegistry registry = RetryRegistry.of(defaultConfig);

        // Database retries - more attempts, exponential backoff
        RetryConfig dbRetryConfig = RetryConfig.custom()
                .maxAttempts(5)
                .waitDuration(Duration.ofMillis(200))
                .retryExceptions(Exception.class)
                .build();
        registry.retry(DATABASE, dbRetryConfig);

        // MSPM retries - external service, longer waits
        RetryConfig mspmRetryConfig = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofSeconds(2))
                .retryExceptions(Exception.class)
                .build();
        registry.retry(MSPM_SERVICE, mspmRetryConfig);

        // Kafka retries
        RetryConfig kafkaRetryConfig = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(500))
                .retryExceptions(Exception.class)
                .build();
        registry.retry(KAFKA, kafkaRetryConfig);

        // Add retry event logging
        registry.getAllRetries().forEach(retry ->
                retry.getEventPublisher()
                        .onRetry(event -> log.debug("Retry '{}' attempt #{} for {}",
                                event.getName(),
                                event.getNumberOfRetryAttempts(),
                                event.getLastThrowable().getMessage())));

        return registry;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // RATE LIMITER CONFIGURATION
    // ═══════════════════════════════════════════════════════════════════════════

    @Bean
    public RateLimiterRegistry rateLimiterRegistry() {
        RateLimiterConfig defaultConfig = RateLimiterConfig.custom()
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .limitForPeriod(100)
                .timeoutDuration(Duration.ofMillis(500))
                .build();

        RateLimiterRegistry registry = RateLimiterRegistry.of(defaultConfig);

        // MSPM rate limiter - don't overwhelm upstream
        RateLimiterConfig mspmConfig = RateLimiterConfig.custom()
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .limitForPeriod(50)
                .timeoutDuration(Duration.ofSeconds(2))
                .build();
        registry.rateLimiter(MSPM_SERVICE, mspmConfig);

        // External API rate limiter
        RateLimiterConfig externalConfig = RateLimiterConfig.custom()
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .limitForPeriod(20)
                .timeoutDuration(Duration.ofSeconds(5))
                .build();
        registry.rateLimiter(EXTERNAL_API, externalConfig);

        // Price service rate limiter
        RateLimiterConfig priceConfig = RateLimiterConfig.custom()
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .limitForPeriod(200)
                .timeoutDuration(Duration.ofMillis(100))
                .build();
        registry.rateLimiter(PRICE_SERVICE, priceConfig);

        return registry;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Get or create a circuit breaker for a service.
     */
    public static CircuitBreaker getCircuitBreaker(CircuitBreakerRegistry registry, String serviceName) {
        return registry.circuitBreaker(serviceName);
    }

    /**
     * Get or create a retry for a service.
     */
    public static Retry getRetry(RetryRegistry registry, String serviceName) {
        return registry.retry(serviceName);
    }

    /**
     * Get or create a rate limiter for a service.
     */
    public static RateLimiter getRateLimiter(RateLimiterRegistry registry, String serviceName) {
        return registry.rateLimiter(serviceName);
    }
}