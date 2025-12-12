package com.vyshali.priceservice.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

/**
 * Resilience4j configuration for upstream price services.
 * 
 * Upstream services:
 * - Filter (real-time prices, 20min delayed)
 * - RCP (EOD snapshot prices)
 * - MSPA (EOD prices)
 */
@Configuration
public class ResilienceConfig {

    // ==================== Circuit Breaker ====================

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerConfig defaultConfig = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(3)
                .slowCallDurationThreshold(Duration.ofSeconds(5))
                .slowCallRateThreshold(50)
                .recordExceptions(IOException.class, TimeoutException.class, RuntimeException.class)
                .build();

        return CircuitBreakerRegistry.of(defaultConfig);
    }

    @Bean
    public CircuitBreaker filterCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("filter", CircuitBreakerConfig.custom()
                .slidingWindowSize(20)
                .failureRateThreshold(40)
                .waitDurationInOpenState(Duration.ofSeconds(60))
                .build());
    }

    @Bean
    public CircuitBreaker rcpCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("rcp", CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .build());
    }

    @Bean
    public CircuitBreaker mspaCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("mspa");
    }

    // ==================== Retry ====================

    @Bean
    public RetryRegistry retryRegistry() {
        RetryConfig defaultConfig = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(500))
                .retryExceptions(IOException.class, TimeoutException.class)
                .build();

        return RetryRegistry.of(defaultConfig);
    }

    @Bean
    public Retry priceRetry(RetryRegistry registry) {
        return registry.retry("priceRetry", RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(300))
                .build());
    }

    // ==================== Time Limiter ====================

    @Bean
    public TimeLimiterRegistry timeLimiterRegistry() {
        TimeLimiterConfig defaultConfig = TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(5))
                .cancelRunningFuture(true)
                .build();

        return TimeLimiterRegistry.of(defaultConfig);
    }

    @Bean
    public TimeLimiter priceTimeLimiter(TimeLimiterRegistry registry) {
        return registry.timeLimiter("priceTimeLimiter", TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(3))
                .build());
    }
}
