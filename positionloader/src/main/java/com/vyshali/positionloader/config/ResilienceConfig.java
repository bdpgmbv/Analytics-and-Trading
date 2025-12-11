package com.vyshali.positionloader.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Resilience4j configuration for circuit breakers and retry policies.
 * 
 * Circuit Breaker States:
 * - CLOSED: Normal operation, requests flow through
 * - OPEN: Failures exceeded threshold, requests fail-fast
 * - HALF_OPEN: Testing if service recovered
 * 
 * Usage:
 * @CircuitBreaker(name = "mspm-service", fallbackMethod = "fallback")
 * @Retry(name = "mspm-service")
 * public Result callMspm() { ... }
 */
@Configuration
public class ResilienceConfig {

    public static final String MSPM_SERVICE = "mspm-service";
    public static final String DATABASE = "database";

    private final LoaderProperties properties;

    public ResilienceConfig(LoaderProperties properties) {
        this.properties = properties;
    }

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        // MSPM Circuit Breaker Config
        CircuitBreakerConfig mspmConfig = CircuitBreakerConfig.custom()
            .failureRateThreshold(50)                     // Open circuit when 50% of calls fail
            .slowCallRateThreshold(80)                    // Open circuit when 80% of calls are slow
            .slowCallDurationThreshold(Duration.ofSeconds(5))  // Consider >5s as slow
            .waitDurationInOpenState(Duration.ofSeconds(30))   // Wait 30s before half-open
            .permittedNumberOfCallsInHalfOpenState(3)    // Test with 3 calls in half-open
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(10)                        // Last 10 calls for metrics
            .minimumNumberOfCalls(5)                      // Need at least 5 calls before opening
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            .build();

        // Database Circuit Breaker Config (more conservative)
        CircuitBreakerConfig dbConfig = CircuitBreakerConfig.custom()
            .failureRateThreshold(70)                     // Higher threshold for DB
            .slowCallRateThreshold(90)
            .slowCallDurationThreshold(Duration.ofSeconds(10))
            .waitDurationInOpenState(Duration.ofSeconds(60))
            .permittedNumberOfCallsInHalfOpenState(5)
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(20)
            .minimumNumberOfCalls(10)
            .build();

        return CircuitBreakerRegistry.of(java.util.Map.of(
            MSPM_SERVICE, mspmConfig,
            DATABASE, dbConfig
        ));
    }

    @Bean
    public CircuitBreaker mspmCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker(MSPM_SERVICE);
    }

    @Bean
    public RetryRegistry retryRegistry() {
        // MSPM Retry Config
        RetryConfig mspmRetryConfig = RetryConfig.custom()
            .maxAttempts(properties.retry().maxAttempts())
            .waitDuration(properties.retry().initialDelay())
            .retryExceptions(
                java.io.IOException.class,
                java.net.SocketTimeoutException.class,
                org.springframework.web.client.ResourceAccessException.class
            )
            .ignoreExceptions(
                IllegalArgumentException.class,
                IllegalStateException.class
            )
            .build();

        // Database Retry Config
        RetryConfig dbRetryConfig = RetryConfig.custom()
            .maxAttempts(3)
            .waitDuration(Duration.ofMillis(500))
            .retryExceptions(
                org.springframework.dao.TransientDataAccessException.class,
                org.springframework.dao.RecoverableDataAccessException.class
            )
            .build();

        return RetryRegistry.of(java.util.Map.of(
            MSPM_SERVICE, mspmRetryConfig,
            DATABASE, dbRetryConfig
        ));
    }

    @Bean
    public Retry mspmRetry(RetryRegistry registry) {
        return registry.retry(MSPM_SERVICE);
    }
}
